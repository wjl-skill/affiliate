package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.ReferralCommissionEntity;
import com.affiliate.platform.affiliate.domain.ReferralRelationshipEntity;
import com.affiliate.platform.affiliate.repository.ReferralCommissionRepository;
import com.affiliate.platform.affiliate.repository.ReferralRelationshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多级推荐佣金服务（重构版 - 接入 PostgreSQL + 多级缓存）
 * <p>
 * 功能：
 * 1. Affiliate 推荐新渠道加入平台
 * 2. 推荐人获得下线转化佣金的额外提成（不影响下线收益）
 * 3. 支持多层级设置（一级、二级）
 * 4. 推荐关系追踪与收益统计
 * 5. 防止循环推荐和推荐链滥用
 * <p>
 * 商业模式：
 * - 一级推荐：推荐人获得直接下线转化佣金的 5%-10%
 * - 二级推荐：推荐人获得二级下线转化佣金的 2%-5%
 * - 平台承担推荐佣金，不扣减下线收益
 * <p>
 * 对标：CJ Affiliate Referral Program、Impact Partner Referral
 */
@Service
public class ReferralCommissionService {

    private final ReferralRelationshipRepository relationshipRepository;
    private final ReferralCommissionRepository commissionRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;

    private static final Duration RELATIONSHIP_CACHE_TTL = Duration.ofHours(2);
    private static final Duration STATS_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration COMMISSION_LIST_CACHE_TTL = Duration.ofMinutes(10);

    // 全局推荐佣金配置
    private static final BigDecimal TIER1_COMMISSION_RATE = new BigDecimal("0.05"); // 一级 5%
    private static final BigDecimal TIER2_COMMISSION_RATE = new BigDecimal("0.02"); // 二级 2%
    private static final int MAX_REFERRAL_DEPTH = 2; // 最多二级

    public ReferralCommissionService(
            ReferralRelationshipRepository relationshipRepository,
            ReferralCommissionRepository commissionRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator
    ) {
        this.relationshipRepository = relationshipRepository;
        this.commissionRepository = commissionRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
    }

    /**
     * 生成推荐链接
     */
    public ReferralLink generateReferralLink(String referrerId, String campaignName) {
        String referralCode = generateReferralCode(referrerId);
        String referralUrl = String.format("https://platform.example.com/signup?ref=%s", referralCode);

        // TODO: 可以将推荐链接持久化，跟踪点击和注册数据

        return new ReferralLink(
                referralCode,
                referrerId,
                referralUrl,
                campaignName,
                0,
                0,
                true,
                Instant.now(),
                null
        );
    }

    /**
     * 注册推荐关系（新渠道注册时调用）
     */
    @Transactional
    public ReferralRelationship registerReferral(String refereeId, String referralCode) {
        // 解析推荐码获取推荐人
        String referrerId = extractReferrerId(referralCode);

        if (referrerId == null) {
            throw new IllegalArgumentException("Invalid referral code");
        }

        // 防止自我推荐
        if (referrerId.equals(refereeId)) {
            throw new IllegalArgumentException("Cannot refer yourself");
        }

        // 检查是否已有推荐关系
        if (relationshipRepository.existsByRefereeId(refereeId)) {
            throw new IllegalStateException("Referral relationship already exists");
        }

        // 检查推荐深度（防止无限链）
        int depth = calculateReferralDepth(referrerId);
        if (depth >= MAX_REFERRAL_DEPTH) {
            depth = MAX_REFERRAL_DEPTH;
        }

        ReferralRelationshipEntity entity = new ReferralRelationshipEntity(
                UUID.randomUUID().toString(),
                refereeId,
                referrerId,
                referralCode,
                depth + 1, // 推荐层级
                ReferralStatus.ACTIVE.name(),
                BigDecimal.ZERO,
                0L,
                Instant.now(),
                null
        );

        relationshipRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.referralRelationship(refereeId));
        cacheManager.evictByPattern("affiliate:referral:list:" + referrerId + ":*");
        cacheManager.evictByPattern("affiliate:referral:stats:" + referrerId);

        return toReferralRelationship(entity);
    }

    /**
     * 获取推荐关系（带缓存）
     */
    public Optional<ReferralRelationship> getReferralRelationship(String refereeId) {
        String cacheKey = keyGenerator.referralRelationship(refereeId);

        return cacheManager.get(
                cacheKey,
                ReferralRelationshipEntity.class,
                RELATIONSHIP_CACHE_TTL,
                () -> relationshipRepository.findByRefereeId(refereeId).orElse(null)
        ).map(this::toReferralRelationship);
    }

    /**
     * 处理转化时的推荐佣金分配
     */
    @Transactional
    public void processReferralCommission(
            String affiliateId,
            String conversionId,
            BigDecimal affiliatePayout
    ) {
        // 查找该渠道的推荐关系
        Optional<ReferralRelationshipEntity> optRelationship =
                relationshipRepository.findByRefereeId(affiliateId);

        if (optRelationship.isEmpty()) {
            return; // 没有推荐关系
        }

        ReferralRelationshipEntity relationship = optRelationship.get();
        if (!ReferralStatus.ACTIVE.name().equals(relationship.getStatus())) {
            return; // 推荐关系已禁用
        }

        // 一级推荐人佣金
        String tier1ReferrerId = relationship.getReferrerId();
        BigDecimal tier1Commission = affiliatePayout.multiply(TIER1_COMMISSION_RATE);

        recordCommission(
                tier1ReferrerId,
                affiliateId,
                conversionId,
                1,
                tier1Commission,
                affiliatePayout
        );

        // 更新一级推荐关系统计
        updateRelationshipStats(affiliateId, tier1Commission);

        // 二级推荐人佣金（如果存在）
        Optional<ReferralRelationshipEntity> optTier1Relationship =
                relationshipRepository.findByRefereeId(tier1ReferrerId);

        if (optTier1Relationship.isPresent()) {
            ReferralRelationshipEntity tier1Relationship = optTier1Relationship.get();
            if (ReferralStatus.ACTIVE.name().equals(tier1Relationship.getStatus())) {
                String tier2ReferrerId = tier1Relationship.getReferrerId();
                BigDecimal tier2Commission = affiliatePayout.multiply(TIER2_COMMISSION_RATE);

                recordCommission(
                        tier2ReferrerId,
                        affiliateId,
                        conversionId,
                        2,
                        tier2Commission,
                        affiliatePayout
                );

                // 更新二级推荐关系统计
                updateRelationshipStats(tier1ReferrerId, tier2Commission);
            }
        }
    }

    /**
     * 记录推荐佣金流水
     */
    @Transactional
    protected void recordCommission(
            String referrerId,
            String refereeId,
            String conversionId,
            int tier,
            BigDecimal commission,
            BigDecimal baseAmount
    ) {
        ReferralCommissionEntity entity = new ReferralCommissionEntity(
                UUID.randomUUID().toString(),
                referrerId,
                refereeId,
                conversionId,
                tier,
                commission,
                baseAmount,
                CommissionStatus.PENDING.name(),
                Instant.now(),
                null
        );

        commissionRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:referral:commission:" + referrerId + ":*");
        cacheManager.evict(keyGenerator.referralStats(referrerId));
    }

    /**
     * 更新推荐关系统计
     */
    @Transactional
    protected void updateRelationshipStats(String refereeId, BigDecimal commission) {
        Optional<ReferralRelationshipEntity> optEntity = relationshipRepository.findByRefereeId(refereeId);
        if (optEntity.isPresent()) {
            ReferralRelationshipEntity entity = optEntity.get();
            entity.setTotalCommissionEarned(entity.getTotalCommissionEarned().add(commission));
            entity.setTotalConversions(entity.getTotalConversions() + 1);
            entity.setLastConversionAt(Instant.now());
            relationshipRepository.save(entity);

            // 失效缓存
            cacheManager.evict(keyGenerator.referralRelationship(refereeId));
            cacheManager.evictByPattern("affiliate:referral:list:" + entity.getReferrerId() + ":*");
        }
    }

    /**
     * 批准推荐佣金（转化审核通过后）
     */
    @Transactional
    public void approveCommission(String commissionId) {
        ReferralCommissionEntity entity = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new IllegalArgumentException("Commission not found"));

        if (!CommissionStatus.PENDING.name().equals(entity.getStatus())) {
            throw new IllegalStateException("Only pending commissions can be approved");
        }

        entity.setStatus(CommissionStatus.APPROVED.name());
        entity.setProcessedAt(Instant.now());
        commissionRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:referral:commission:" + entity.getReferrerId() + ":*");
        cacheManager.evict(keyGenerator.referralStats(entity.getReferrerId()));
    }

    /**
     * 拒绝推荐佣金（转化被拒绝时）
     */
    @Transactional
    public void rejectCommission(String commissionId, String reason) {
        ReferralCommissionEntity entity = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new IllegalArgumentException("Commission not found"));

        if (!CommissionStatus.PENDING.name().equals(entity.getStatus())) {
            throw new IllegalStateException("Only pending commissions can be rejected");
        }

        entity.setStatus(CommissionStatus.REJECTED.name());
        entity.setCommission(BigDecimal.ZERO);
        entity.setProcessedAt(Instant.now());
        commissionRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:referral:commission:" + entity.getReferrerId() + ":*");
        cacheManager.evict(keyGenerator.referralStats(entity.getReferrerId()));
    }

    /**
     * 获取推荐人的下线列表
     */
    public List<ReferralRelationship> getReferrals(String referrerId, int tier) {
        List<ReferralRelationshipEntity> entities;

        if (tier == 0) {
            entities = relationshipRepository.findByReferrerIdOrderByCreatedAtDesc(referrerId);
        } else {
            entities = relationshipRepository.findByReferrerIdAndTierOrderByCreatedAtDesc(referrerId, tier);
        }

        return entities.stream()
                .map(this::toReferralRelationship)
                .collect(Collectors.toList());
    }

    /**
     * 获取推荐人的佣金统计（带缓存）
     */
    public ReferralStats getReferralStats(String referrerId) {
        String cacheKey = keyGenerator.referralStats(referrerId);

        return cacheManager.get(
                cacheKey,
                ReferralStats.class,
                STATS_CACHE_TTL,
                () -> calculateReferralStats(referrerId)
        ).orElseGet(() -> calculateReferralStats(referrerId));
    }

    /**
     * 计算推荐统计
     */
    private ReferralStats calculateReferralStats(String referrerId) {
        long totalReferrals = relationshipRepository.countByReferrerId(referrerId);
        long totalConversions = commissionRepository.countByReferrerId(referrerId);

        BigDecimal totalEarned = commissionRepository.sumApprovedCommissionByReferrer(referrerId);
        if (totalEarned == null) totalEarned = BigDecimal.ZERO;

        BigDecimal pendingEarned = commissionRepository.sumPendingCommissionByReferrer(referrerId);
        if (pendingEarned == null) pendingEarned = BigDecimal.ZERO;

        return new ReferralStats(
                referrerId,
                (int) totalReferrals,
                totalConversions,
                totalEarned,
                pendingEarned
        );
    }

    /**
     * 获取推荐佣金明细
     */
    public List<ReferralCommissionEntry> getCommissionHistory(
            String referrerId,
            CommissionStatus status,
            Instant from,
            Instant to
    ) {
        List<ReferralCommissionEntity> entities;

        if (status != null) {
            entities = commissionRepository.findByReferrerStatusAndTimeRange(
                    referrerId,
                    status.name(),
                    from,
                    to
            );
        } else {
            entities = commissionRepository.findByReferrerAndTimeRange(referrerId, from, to);
        }

        return entities.stream()
                .map(this::toReferralCommissionEntry)
                .collect(Collectors.toList());
    }

    /**
     * 暂停/恢复推荐关系
     */
    @Transactional
    public void updateReferralStatus(String refereeId, ReferralStatus newStatus) {
        ReferralRelationshipEntity entity = relationshipRepository.findByRefereeId(refereeId)
                .orElseThrow(() -> new IllegalArgumentException("Referral relationship not found"));

        entity.setStatus(newStatus.name());
        relationshipRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.referralRelationship(refereeId));
        cacheManager.evictByPattern("affiliate:referral:list:" + entity.getReferrerId() + ":*");
    }

    /**
     * 获取推荐排行榜（Top Referrers）
     */
    public List<ReferrerLeaderboard> getTopReferrers(int limit) {
        List<Object[]> results = commissionRepository.findTopReferrersByEarnings();

        return results.stream()
                .limit(limit)
                .map(row -> {
                    String referrerId = (String) row[0];
                    BigDecimal totalEarned = (BigDecimal) row[1];
                    ReferralStats stats = getReferralStats(referrerId);

                    return new ReferrerLeaderboard(
                            referrerId,
                            stats.totalReferrals(),
                            stats.totalConversions(),
                            totalEarned
                    );
                })
                .collect(Collectors.toList());
    }

    // ========== 私有辅助方法 ==========

    private String generateReferralCode(String referrerId) {
        return Base64.getUrlEncoder().encodeToString(referrerId.getBytes())
                .replaceAll("=", "");
    }

    private String extractReferrerId(String referralCode) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(referralCode);
            return new String(decoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int calculateReferralDepth(String affiliateId) {
        int depth = 0;
        String current = affiliateId;

        while (current != null && depth < MAX_REFERRAL_DEPTH) {
            Optional<ReferralRelationshipEntity> relationship =
                    relationshipRepository.findByRefereeId(current);
            if (relationship.isEmpty()) {
                break;
            }
            current = relationship.get().getReferrerId();
            depth++;
        }

        return depth;
    }

    private ReferralRelationship toReferralRelationship(ReferralRelationshipEntity entity) {
        return new ReferralRelationship(
                entity.getId(),
                entity.getRefereeId(),
                entity.getReferrerId(),
                entity.getReferralCode(),
                entity.getTier(),
                ReferralStatus.valueOf(entity.getStatus()),
                entity.getTotalCommissionEarned(),
                entity.getTotalConversions(),
                entity.getCreatedAt(),
                entity.getLastConversionAt()
        );
    }

    private ReferralCommissionEntry toReferralCommissionEntry(ReferralCommissionEntity entity) {
        return new ReferralCommissionEntry(
                entity.getId(),
                entity.getReferrerId(),
                entity.getRefereeId(),
                entity.getConversionId(),
                entity.getTier(),
                entity.getCommission(),
                entity.getBaseAmount(),
                CommissionStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getProcessedAt()
        );
    }

    // ========== 数据记录 ==========

    public record ReferralLink(
            String referralCode,
            String referrerId,
            String referralUrl,
            String campaignName,
            int clicks,
            int signups,
            boolean active,
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record ReferralRelationship(
            String id,
            String refereeId,              // 被推荐人（下线）
            String referrerId,             // 推荐人（上线）
            String referralCode,           // 推荐码
            int tier,                      // 推荐层级（1=直接下线，2=二级下线）
            ReferralStatus status,
            BigDecimal totalCommissionEarned,
            long totalConversions,
            Instant createdAt,
            Instant lastConversionAt
    ) {}

    public record ReferralCommissionEntry(
            String id,
            String referrerId,
            String refereeId,
            String conversionId,
            int tier,
            BigDecimal commission,
            BigDecimal baseAmount,         // 原始转化佣金
            CommissionStatus status,
            Instant createdAt,
            Instant processedAt
    ) {}

    public record ReferralStats(
            String referrerId,
            int totalReferrals,            // 总推荐人数
            long totalConversions,         // 下线总转化数
            BigDecimal totalEarned,        // 总推荐佣金收益
            BigDecimal pendingEarned       // 待审核佣金
    ) {}

    public record ReferrerLeaderboard(
            String referrerId,
            int totalReferrals,
            long totalConversions,
            BigDecimal totalEarned
    ) {}

    public enum ReferralStatus {
        ACTIVE,      // 活跃
        PAUSED,      // 暂停
        TERMINATED   // 终止
    }

    public enum CommissionStatus {
        PENDING,     // 待审核
        APPROVED,    // 已批准
        REJECTED,    // 已拒绝
        PAID         // 已支付
    }
}
