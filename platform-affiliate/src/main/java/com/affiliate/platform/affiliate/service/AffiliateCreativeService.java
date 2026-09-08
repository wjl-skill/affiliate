package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.CreativeEntity;
import com.affiliate.platform.affiliate.repository.CreativeRepository;
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
 * 网盟素材/广告创意管理服务（重构版 - 接入 PostgreSQL + 多级缓存）
 * <p>
 * 功能：
 * 1. Banner 图片素材管理（多尺寸、多语言）
 * 2. 文本链接模板
 * 3. Email 营销模板
 * 4. 社交媒体素材包
 * 5. 素材性能追踪（CTR、CR 分析）
 * 6. 素材审核与合规检查
 * <p>
 * 对标：CJ Creative Assets、ShareASale Creatives、Impact Creative Library
 */
@Service
public class AffiliateCreativeService {

    private final CreativeRepository creativeRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    private static final Duration CREATIVE_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration CREATIVE_LIST_CACHE_TTL = Duration.ofMinutes(15);

    public AffiliateCreativeService(
            CreativeRepository creativeRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.creativeRepository = creativeRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建素材
     */
    @Transactional
    public Creative createCreative(
            String offerId,
            CreativeType type,
            String name,
            String description,
            Map<String, String> assets,
            List<String> languages,
            Map<String, String> metadata
    ) {
        String creativeId = generateCreativeId();

        CreativeEntity entity = new CreativeEntity(
                creativeId,
                offerId,
                type.name(),
                name,
                description,
                serializeMap(assets),
                serializeList(languages),
                serializeMap(metadata),
                CreativeStatus.PENDING_REVIEW.name(),
                null,
                0L,
                0L,
                Instant.now(),
                null
        );

        creativeRepository.save(entity);

        // 失效缓存
        cacheManager.evictByPattern("affiliate:creative:offer:" + offerId + ":*");

        return toCreative(entity);
    }

    /**
     * 批量上传 Banner 素材
     */
    @Transactional
    public List<Creative> uploadBanners(
            String offerId,
            String campaignName,
            Map<BannerSize, String> bannerUrls,
            String landingPageUrl,
            List<String> languages
    ) {
        List<Creative> uploadedCreatives = new ArrayList<>();

        for (Map.Entry<BannerSize, String> entry : bannerUrls.entrySet()) {
            BannerSize size = entry.getKey();
            String imageUrl = entry.getValue();

            Map<String, String> assets = Map.of(
                    "imageUrl", imageUrl,
                    "landingPageUrl", landingPageUrl,
                    "width", String.valueOf(size.width),
                    "height", String.valueOf(size.height)
            );

            Creative creative = createCreative(
                    offerId,
                    CreativeType.BANNER,
                    campaignName + " - " + size.name(),
                    "Banner " + size.width + "x" + size.height,
                    assets,
                    languages,
                    Map.of("size", size.name())
            );

            uploadedCreatives.add(creative);
        }

        return uploadedCreatives;
    }

    /**
     * 创建文本链接
     */
    @Transactional
    public Creative createTextLink(
            String offerId,
            String linkText,
            String landingPageUrl,
            String description
    ) {
        Map<String, String> assets = Map.of(
                "linkText", linkText,
                "landingPageUrl", landingPageUrl
        );

        return createCreative(
                offerId,
                CreativeType.TEXT_LINK,
                "Text Link: " + linkText,
                description,
                assets,
                List.of("en"),
                Map.of()
        );
    }

    /**
     * 创建 Email 模板
     */
    @Transactional
    public Creative createEmailTemplate(
            String offerId,
            String subject,
            String htmlBody,
            String plainTextBody,
            List<String> languages
    ) {
        Map<String, String> assets = Map.of(
                "subject", subject,
                "htmlBody", htmlBody,
                "plainTextBody", plainTextBody
        );

        return createCreative(
                offerId,
                CreativeType.EMAIL_TEMPLATE,
                "Email: " + subject,
                "Email marketing template",
                assets,
                languages,
                Map.of()
        );
    }

    /**
     * 创建社交媒体素材
     */
    @Transactional
    public Creative createSocialMediaPost(
            String offerId,
            SocialPlatform platform,
            String postText,
            String imageUrl,
            List<String> hashtags
    ) {
        Map<String, String> assets = new HashMap<>();
        assets.put("platform", platform.name());
        assets.put("postText", postText);
        if (imageUrl != null) {
            assets.put("imageUrl", imageUrl);
        }
        assets.put("hashtags", String.join(",", hashtags));

        return createCreative(
                offerId,
                CreativeType.SOCIAL_MEDIA,
                platform.name() + " Post",
                "Social media creative for " + platform.name(),
                assets,
                List.of("en"),
                Map.of("platform", platform.name())
        );
    }

    /**
     * 审核素材
     */
    @Transactional
    public Creative reviewCreative(
            String creativeId,
            boolean approved,
            String reviewerNote
    ) {
        CreativeEntity entity = creativeRepository.findById(creativeId)
                .orElseThrow(() -> new IllegalArgumentException("Creative not found"));

        entity.setStatus(approved ? CreativeStatus.APPROVED.name() : CreativeStatus.REJECTED.name());
        entity.setReviewerNote(reviewerNote);
        entity.setReviewedAt(Instant.now());
        creativeRepository.save(entity);

        // 失效缓存
        cacheManager.evict(keyGenerator.creative(creativeId));
        cacheManager.evictByPattern("affiliate:creative:offer:" + entity.getOfferId() + ":*");

        return toCreative(entity);
    }

    /**
     * 获取素材（带缓存）
     */
    public Optional<Creative> getCreative(String creativeId) {
        String cacheKey = keyGenerator.creative(creativeId);

        return cacheManager.get(
                cacheKey,
                CreativeEntity.class,
                CREATIVE_CACHE_TTL,
                () -> creativeRepository.findById(creativeId).orElse(null)
        ).map(this::toCreative);
    }

    /**
     * 获取 Offer 的所有素材（带缓存）
     */
    public List<Creative> getCreativesByOffer(String offerId, CreativeType type, CreativeStatus status) {
        String cacheKey = "affiliate:creative:offer:" + offerId + ":type:" +
                (type != null ? type.name() : "all") + ":status:" +
                (status != null ? status.name() : "all");

        return cacheManager.get(
                cacheKey,
                List.class,
                CREATIVE_LIST_CACHE_TTL,
                () -> {
                    List<CreativeEntity> entities;
                    if (type != null && status != null) {
                        entities = creativeRepository.findByOfferIdAndTypeAndStatusOrderByCreatedAtDesc(
                                offerId, type.name(), status.name());
                    } else if (type != null) {
                        entities = creativeRepository.findByOfferIdAndTypeOrderByCreatedAtDesc(
                                offerId, type.name());
                    } else if (status != null) {
                        entities = creativeRepository.findByOfferIdAndStatusOrderByCreatedAtDesc(
                                offerId, status.name());
                    } else {
                        entities = creativeRepository.findByOfferIdOrderByCreatedAtDesc(offerId);
                    }
                    return entities;
                }
        ).map(list -> ((List<CreativeEntity>) list).stream()
                .map(this::toCreative)
                .collect(Collectors.toList())
        ).orElse(List.of());
    }

    /**
     * 获取热门素材（按 CTR 排序）
     */
    public List<CreativeWithStats> getTopPerformingCreatives(
            String offerId,
            CreativeType type,
            int limit
    ) {
        return getCreativesByOffer(offerId, type, CreativeStatus.APPROVED).stream()
                .map(creative -> {
                    long impressions = creative.clicks() * 10; // 简化：假设展示量是点击量的10倍
                    double ctr = impressions > 0 ? (double) creative.clicks() / impressions * 100 : 0.0;
                    double cr = creative.clicks() > 0 ?
                            (double) creative.conversions() / creative.clicks() * 100 : 0.0;

                    return new CreativeWithStats(creative, impressions, ctr, cr);
                })
                .sorted(Comparator.comparingDouble(CreativeWithStats::ctr).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 记录素材点击
     */
    @Transactional
    public void trackClick(String creativeId, String clickId) {
        creativeRepository.incrementClicks(creativeId);

        // 失效缓存
        cacheManager.evict(keyGenerator.creative(creativeId));

        // 获取offerId并失效列表缓存
        creativeRepository.findById(creativeId).ifPresent(entity -> {
            cacheManager.evictByPattern("affiliate:creative:offer:" + entity.getOfferId() + ":*");
        });
    }

    /**
     * 记录素材转化
     */
    @Transactional
    public void trackConversion(String creativeId) {
        creativeRepository.incrementConversions(creativeId);

        // 失效缓存
        cacheManager.evict(keyGenerator.creative(creativeId));

        creativeRepository.findById(creativeId).ifPresent(entity -> {
            cacheManager.evictByPattern("affiliate:creative:offer:" + entity.getOfferId() + ":*");
        });
    }

    /**
     * 生成素材跟踪链接
     */
    public String generateTrackingUrl(
            String creativeId,
            String baseClickUrl,
            String affiliateId,
            String offerId
    ) {
        if (!creativeRepository.existsById(creativeId)) {
            throw new IllegalArgumentException("Creative not found: " + creativeId);
        }

        return String.format("%s?aff_id=%s&offer_id=%s&creative_id=%s",
                baseClickUrl, affiliateId, offerId, creativeId);
    }

    /**
     * 导出素材包（批量下载）
     */
    public CreativePackage exportCreativePackage(String offerId, List<CreativeType> types) {
        List<Creative> selectedCreatives = getCreativesByOffer(offerId, null, CreativeStatus.APPROVED)
                .stream()
                .filter(c -> types.contains(c.type()))
                .collect(Collectors.toList());

        Map<CreativeType, List<Creative>> groupedByType = selectedCreatives.stream()
                .collect(Collectors.groupingBy(Creative::type));

        return new CreativePackage(
                offerId,
                "Creative Package - " + offerId,
                groupedByType,
                selectedCreatives.size(),
                Instant.now()
        );
    }

    /**
     * 批量激活/停用素材
     */
    @Transactional
    public void bulkUpdateStatus(List<String> creativeIds, CreativeStatus newStatus) {
        creativeRepository.bulkUpdateStatus(creativeIds, newStatus.name());

        // 失效所有相关缓存
        for (String creativeId : creativeIds) {
            cacheManager.evict(keyGenerator.creative(creativeId));
        }

        // 失效所有offer列表缓存（因为不知道具体哪些offer）
        cacheManager.evictByPattern("affiliate:creative:offer:*");
    }

    /**
     * 搜索素材
     */
    public List<Creative> searchCreatives(
            String keyword,
            CreativeType type,
            List<String> languages,
            CreativeStatus status
    ) {
        List<CreativeEntity> entities;

        if (keyword != null && !keyword.isEmpty()) {
            entities = creativeRepository.searchByKeyword(keyword);
        } else {
            entities = creativeRepository.findAll();
        }

        return entities.stream()
                .filter(e -> type == null || type.name().equals(e.getType()))
                .filter(e -> status == null || status.name().equals(e.getStatus()))
                .filter(e -> languages == null || languages.isEmpty() ||
                        matchesLanguage(e.getLanguages(), languages))
                .map(this::toCreative)
                .collect(Collectors.toList());
    }

    /**
     * 删除素材
     */
    @Transactional
    public void deleteCreative(String creativeId) {
        Optional<CreativeEntity> optEntity = creativeRepository.findById(creativeId);
        if (optEntity.isPresent()) {
            CreativeEntity entity = optEntity.get();
            creativeRepository.deleteById(creativeId);

            // 失效缓存
            cacheManager.evict(keyGenerator.creative(creativeId));
            cacheManager.evictByPattern("affiliate:creative:offer:" + entity.getOfferId() + ":*");
        }
    }

    /**
     * 统计Offer的素材数量
     */
    public long countCreativesByOffer(String offerId) {
        return creativeRepository.countByOfferId(offerId);
    }

    // ========== 私有辅助方法 ==========

    private boolean matchesLanguage(String languagesJson, List<String> targetLanguages) {
        List<String> languages = deserializeList(languagesJson);
        return languages.stream().anyMatch(targetLanguages::contains);
    }

    private String generateCreativeId() {
        return "creative_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String serializeMap(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String serializeList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private Map<String, String> deserializeMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private List<String> deserializeList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Creative toCreative(CreativeEntity entity) {
        return new Creative(
                entity.getId(),
                entity.getOfferId(),
                CreativeType.valueOf(entity.getType()),
                entity.getName(),
                entity.getDescription(),
                deserializeMap(entity.getAssets()),
                deserializeList(entity.getLanguages()),
                deserializeMap(entity.getMetadata()),
                CreativeStatus.valueOf(entity.getStatus()),
                entity.getReviewerNote(),
                entity.getClicks(),
                entity.getConversions(),
                entity.getCreatedAt(),
                entity.getReviewedAt()
        );
    }

    // ========== 数据记录 ==========

    public record Creative(
            String id,
            String offerId,
            CreativeType type,
            String name,
            String description,
            Map<String, String> assets,
            List<String> languages,
            Map<String, String> metadata,
            CreativeStatus status,
            String reviewerNote,
            long clicks,
            long conversions,
            Instant createdAt,
            Instant reviewedAt
    ) {}

    public record CreativeWithStats(
            Creative creative,
            long impressions,
            double ctr,
            double cr
    ) {}

    public record CreativePackage(
            String offerId,
            String name,
            Map<CreativeType, List<Creative>> creativesByType,
            int totalCount,
            Instant generatedAt
    ) {}

    public enum CreativeType {
        BANNER,
        TEXT_LINK,
        EMAIL_TEMPLATE,
        SOCIAL_MEDIA,
        VIDEO,
        NATIVE,
        PRODUCT_FEED,
        COUPON
    }

    public enum CreativeStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        PAUSED,
        ARCHIVED
    }

    public enum BannerSize {
        LEADERBOARD_728x90(728, 90),
        MEDIUM_RECTANGLE_300x250(300, 250),
        WIDE_SKYSCRAPER_160x600(160, 600),
        LARGE_RECTANGLE_336x280(336, 280),
        MOBILE_BANNER_320x50(320, 50),
        MOBILE_LEADERBOARD_320x100(320, 100),
        BILLBOARD_970x250(970, 250),
        SQUARE_250x250(250, 250),
        SMALL_SQUARE_200x200(200, 200),
        HALF_PAGE_300x600(300, 600);

        final int width;
        final int height;

        BannerSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public enum SocialPlatform {
        FACEBOOK,
        INSTAGRAM,
        TWITTER,
        LINKEDIN,
        PINTEREST,
        TIKTOK,
        YOUTUBE,
        REDDIT
    }
}
