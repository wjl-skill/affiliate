package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.OfferApplicationEntity;
import com.affiliate.platform.affiliate.repository.OfferApplicationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Offer 渠道申请审批工作流服务（重构版 - 接入 PostgreSQL + 多级缓存）
 * <p>
 * 功能：
 * 1. 渠道申请访问私有 Offer
 * 2. 广告主审批/拒绝申请
 * 3. 自动审批规则（基于渠道质量分、等级）
 * 4. 申请历史追踪
 */
@Service
public class OfferApprovalWorkflowService {

    private final OfferApplicationRepository applicationRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OfferService offerService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private S2sPostbackService postbackService;

    public void setOfferService(OfferService offerService) {
        this.offerService = offerService;
    }

    public void setPostbackService(S2sPostbackService postbackService) {
        this.postbackService = postbackService;
    }

    private static final Duration APPLICATION_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration ACCESS_CACHE_TTL = Duration.ofMinutes(30);

    public OfferApprovalWorkflowService(
            OfferApplicationRepository applicationRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.applicationRepository = applicationRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 渠道申请访问 Offer
     */
    @Transactional
    public OfferApplication applyForOffer(
            String offerId,
            String affiliateId,
            String promotionPlan,
            List<String> trafficSources
    ) {
        // 检查是否已申请
        Optional<OfferApplicationEntity> existing = applicationRepository.findByOfferIdAndAffiliateId(
                offerId, affiliateId);

        if (existing.isPresent() && ApplicationStatus.PENDING.name().equals(existing.get().getStatus())) {
            return toOfferApplication(existing.get());
        }

        // 创建新申请
        String appId = generateApplicationId(offerId, affiliateId);
        OfferApplicationEntity entity = new OfferApplicationEntity(
                appId,
                offerId,
                affiliateId,
                promotionPlan,
                serializeList(trafficSources),
                ApplicationStatus.PENDING.name(),
                null,
                null,
                Instant.now(),
                null
        );

        applicationRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:application:affiliate:" + affiliateId + ":*");
        cacheManager.evictByPattern("affiliate:application:offer:" + offerId + ":*");

        // 尝试自动审批
        tryAutoApprove(entity);

        return toOfferApplication(applicationRepository.findById(appId).orElse(entity));
    }

    /**
     * 广告主审批申请
     */
    @Transactional
    public OfferApplication approveApplication(String applicationId, String reviewerId, String note) {
        OfferApplicationEntity entity = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!ApplicationStatus.PENDING.name().equals(entity.getStatus())) {
            throw new IllegalStateException("Application already processed");
        }

        entity.setStatus(ApplicationStatus.APPROVED.name());
        entity.setReviewerId(reviewerId);
        entity.setReviewNote(note);
        entity.setReviewedAt(Instant.now());
        applicationRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.offerApplication(applicationId));
        cacheManager.evictByPattern("affiliate:application:affiliate:" + entity.getAffiliateId() + ":*");
        cacheManager.evictByPattern("affiliate:application:offer:" + entity.getOfferId() + ":*");
        cacheManager.evict(keyGenerator.offerAccess(entity.getOfferId(), entity.getAffiliateId()));

        return toOfferApplication(entity);
    }

    /**
     * 广告主拒绝申请
     */
    @Transactional
    public OfferApplication rejectApplication(String applicationId, String reviewerId, String reason) {
        OfferApplicationEntity entity = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!ApplicationStatus.PENDING.name().equals(entity.getStatus())) {
            throw new IllegalStateException("Application already processed");
        }

        entity.setStatus(ApplicationStatus.REJECTED.name());
        entity.setReviewerId(reviewerId);
        entity.setReviewNote(reason);
        entity.setReviewedAt(Instant.now());
        applicationRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.offerApplication(applicationId));
        cacheManager.evictByPattern("affiliate:application:affiliate:" + entity.getAffiliateId() + ":*");
        cacheManager.evictByPattern("affiliate:application:offer:" + entity.getOfferId() + ":*");

        return toOfferApplication(entity);
    }

    /**
     * 检查渠道是否有权限推广 Offer（带缓存）
     */
    public boolean hasAccess(String offerId, String affiliateId) {
        String cacheKey = keyGenerator.offerAccess(offerId, affiliateId);

        return cacheManager.get(
                cacheKey,
                Boolean.class,
                ACCESS_CACHE_TTL,
                () -> {
                    if (applicationRepository.hasAccess(offerId, affiliateId)) {
                        return true;
                    }
                    if (offerService != null) {
                        return offerService.find(offerId)
                                .map(offer -> offer.isAvailable()
                                        && offer.title() != null
                                        && !offer.title().toUpperCase().contains("PRIVATE"))
                                .orElse(false);
                    }
                    return false;
                }
        ).orElse(false);
    }

    /**
     * 获取申请详情（带缓存）
     */
    public Optional<OfferApplication> getApplication(String applicationId) {
        String cacheKey = keyGenerator.offerApplication(applicationId);

        return cacheManager.get(
                cacheKey,
                OfferApplicationEntity.class,
                APPLICATION_CACHE_TTL,
                () -> applicationRepository.findById(applicationId).orElse(null)
        ).map(this::toOfferApplication);
    }

    /**
     * 获取渠道的所有申请
     */
    public List<OfferApplication> getApplicationsByAffiliate(String affiliateId) {
        String cacheKey = "affiliate:application:affiliate:" + affiliateId + ":all";

        return cacheManager.get(
                cacheKey,
                List.class,
                APPLICATION_CACHE_TTL,
                () -> applicationRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId)
        ).map(list -> ((List<OfferApplicationEntity>) list).stream()
                .map(this::toOfferApplication)
                .collect(Collectors.toList())
        ).orElse(List.of());
    }

    /**
     * 获取 Offer 的所有申请
     */
    public List<OfferApplication> getApplicationsByOffer(String offerId) {
        String cacheKey = "affiliate:application:offer:" + offerId + ":all";

        return cacheManager.get(
                cacheKey,
                List.class,
                APPLICATION_CACHE_TTL,
                () -> applicationRepository.findByOfferIdOrderByCreatedAtDesc(offerId)
        ).map(list -> ((List<OfferApplicationEntity>) list).stream()
                .map(this::toOfferApplication)
                .collect(Collectors.toList())
        ).orElse(List.of());
    }

    /**
     * 获取待审批的申请
     */
    public List<OfferApplication> getPendingApplications() {
        List<OfferApplicationEntity> entities = applicationRepository.findByStatusOrderByCreatedAtAsc(
                ApplicationStatus.PENDING.name());

        return entities.stream()
                .map(this::toOfferApplication)
                .collect(Collectors.toList());
    }

    /**
     * 获取渠道已批准的Offer列表
     */
    public List<String> getApprovedOffers(String affiliateId) {
        List<OfferApplicationEntity> approved = applicationRepository.findApprovedApplicationsByAffiliate(affiliateId);

        return approved.stream()
                .map(OfferApplicationEntity::getOfferId)
                .collect(Collectors.toList());
    }

    /**
     * 统计待审批数量
     */
    public long countPendingApplications() {
        return applicationRepository.countByStatus(ApplicationStatus.PENDING.name());
    }

    /**
     * 统计Offer的申请数量
     */
    public long countApplicationsByOffer(String offerId) {
        return applicationRepository.countByOfferId(offerId);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 尝试自动审批（基于渠道等级与账户风控状态）
     */
    private void tryAutoApprove(OfferApplicationEntity application) {
        if (application == null || postbackService == null) {
            return;
        }
        postbackService.findPartner(application.getAffiliateId()).ifPresent(partner -> {
            if (partner.status() == com.affiliate.platform.affiliate.domain.AffiliatePartner.Status.SUSPENDED) {
                application.setStatus(ApplicationStatus.REJECTED.name());
                application.setReviewerId("SYSTEM_AUTO_GUARD");
                application.setReviewNote("Auto-rejected: partner account is suspended");
                application.setReviewedAt(Instant.now());
                applicationRepository.save(application);
            } else if (partner.status() == com.affiliate.platform.affiliate.domain.AffiliatePartner.Status.ACTIVE
                    && (partner.tier() == com.affiliate.platform.affiliate.domain.AffiliatePartner.Tier.GOLD
                    || partner.tier() == com.affiliate.platform.affiliate.domain.AffiliatePartner.Tier.VIP)) {
                application.setStatus(ApplicationStatus.APPROVED.name());
                application.setReviewerId("SYSTEM_AUTO_APPROVAL");
                application.setReviewNote("Auto-approved for trusted " + partner.tier().name() + " partner");
                application.setReviewedAt(Instant.now());
                applicationRepository.save(application);
                cacheManager.evict(keyGenerator.offerAccess(application.getOfferId(), application.getAffiliateId()));
            }
        });
    }

    private String generateApplicationId(String offerId, String affiliateId) {
        return "app_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String serializeList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> deserializeList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private OfferApplication toOfferApplication(OfferApplicationEntity entity) {
        return new OfferApplication(
                entity.getId(),
                entity.getOfferId(),
                entity.getAffiliateId(),
                entity.getPromotionPlan(),
                deserializeList(entity.getTrafficSources()),
                ApplicationStatus.valueOf(entity.getStatus()),
                entity.getReviewerId(),
                entity.getReviewNote(),
                entity.getCreatedAt(),
                entity.getReviewedAt()
        );
    }

    // ========== 数据记录 ==========

    public record OfferApplication(
            String id,
            String offerId,
            String affiliateId,
            String promotionPlan,
            List<String> trafficSources,
            ApplicationStatus status,
            String reviewerId,
            String reviewNote,
            Instant createdAt,
            Instant reviewedAt
    ) {}

    public enum ApplicationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
