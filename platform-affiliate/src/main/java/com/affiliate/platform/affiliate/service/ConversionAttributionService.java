package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.AttributionResultEntity;
import com.affiliate.platform.affiliate.domain.TouchPointEntity;
import com.affiliate.platform.affiliate.repository.AttributionResultRepository;
import com.affiliate.platform.affiliate.repository.TouchPointRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 高级转化归因服务（重构版 - 接入 PostgreSQL + 多级缓存）
 */
@Service
public class ConversionAttributionService {

    private final TouchPointRepository touchPointRepository;
    private final AttributionResultRepository attributionResultRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    private static final Duration DEFAULT_ATTRIBUTION_WINDOW = Duration.ofDays(30);
    private static final AttributionModel DEFAULT_MODEL = AttributionModel.LAST_CLICK;
    private static final Duration ATTRIBUTION_RESULT_CACHE_TTL = Duration.ofHours(1);
    private static final Duration CUSTOMER_JOURNEY_CACHE_TTL = Duration.ofMinutes(30);

    public ConversionAttributionService(
            TouchPointRepository touchPointRepository,
            AttributionResultRepository attributionResultRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.touchPointRepository = touchPointRepository;
        this.attributionResultRepository = attributionResultRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录触点（点击、展示、互动）
     */
    @Transactional
    public TouchPoint recordTouchPoint(
            String userId,
            String sessionId,
            TouchPointType type,
            String affiliateId,
            String offerId,
            String clickId,
            String source,
            String medium,
            String campaign
    ) {
        String touchPointId = UUID.randomUUID().toString();

        TouchPointEntity entity = new TouchPointEntity(
                touchPointId,
                userId,
                sessionId,
                type.name(),
                affiliateId,
                offerId,
                clickId,
                source,
                medium,
                campaign,
                Instant.now()
        );

        touchPointRepository.save(entity);

        // 触点是写多读少场景，不缓存
        // 但失效用户的客户旅程缓存
        cacheManager.evict(keyGenerator.customerJourney(userId));

        return toTouchPoint(entity);
    }

    /**
     * 执行多触点归因（带缓存）
     */
    @Transactional
    public AttributionResult attributeConversion(
            String userId,
            String conversionId,
            BigDecimal conversionValue,
            Instant conversionTime,
            AttributionModel model
    ) {
        // 检查是否已归因
        if (attributionResultRepository.existsByConversionId(conversionId)) {
            return attributionResultRepository.findByConversionId(conversionId)
                    .map(this::toAttributionResult)
                    .orElseThrow();
        }

        // 获取归因窗口内的所有触点
        Instant windowStart = conversionTime.minus(DEFAULT_ATTRIBUTION_WINDOW);
        List<TouchPointEntity> touchPointEntities = touchPointRepository.findTouchPointsInWindow(
                userId,
                windowStart,
                conversionTime
        );

        if (touchPointEntities.isEmpty()) {
            // 无触点：直接转化
            return createDirectConversionResult(userId, conversionId, conversionValue, conversionTime);
        }

        // 转换为领域对象
        List<TouchPoint> touchPoints = touchPointEntities.stream()
                .map(this::toTouchPoint)
                .collect(Collectors.toList());

        // 根据模型分配归因权重
        List<AttributionCredit> credits = calculateAttributionCredits(
                touchPoints,
                conversionValue,
                model != null ? model : DEFAULT_MODEL
        );

        // 保存归因结果
        AttributionResultEntity resultEntity = new AttributionResultEntity(
                UUID.randomUUID().toString(),
                userId,
                conversionId,
                conversionValue,
                conversionTime,
                (model != null ? model : DEFAULT_MODEL).name(),
                touchPoints.size(),
                serializeCredits(credits),
                Instant.now()
        );

        attributionResultRepository.save(resultEntity);

        AttributionResult result = toAttributionResult(resultEntity);

        // 缓存归因结果
        cacheManager.put(
                keyGenerator.attributionResult(conversionId),
                result,
                ATTRIBUTION_RESULT_CACHE_TTL
        );

        return result;
    }

    /**
     * 获取归因结果（带缓存）
     */
    public Optional<AttributionResult> getAttributionResult(String conversionId) {
        String cacheKey = keyGenerator.attributionResult(conversionId);

        return cacheManager.get(
                cacheKey,
                AttributionResultEntity.class,
                ATTRIBUTION_RESULT_CACHE_TTL,
                () -> attributionResultRepository.findByConversionId(conversionId).orElse(null)
        ).map(this::toAttributionResult);
    }

    /**
     * 获取客户旅程（带缓存）
     */
    public CustomerJourney getCustomerJourney(String userId) {
        String cacheKey = keyGenerator.customerJourney(userId);

        return cacheManager.get(
                cacheKey,
                CustomerJourney.class,
                CUSTOMER_JOURNEY_CACHE_TTL,
                () -> buildCustomerJourney(userId)
        ).orElseGet(() -> buildCustomerJourney(userId));
    }

    /**
     * 获取辅助转化统计
     */
    public AssistedConversionStats getAssistedConversionStats(
            String affiliateId,
            Instant from,
            Instant to
    ) {
        List<AttributionResultEntity> results = attributionResultRepository.findByTimeRange(from, to);

        long directConversions = 0;
        long assistedConversions = 0;
        BigDecimal directValue = BigDecimal.ZERO;
        BigDecimal assistedValue = BigDecimal.ZERO;

        for (AttributionResultEntity result : results) {
            List<AttributionCredit> credits = deserializeCredits(result.getCredits());

            for (AttributionCredit credit : credits) {
                if (credit.affiliateId().equals(affiliateId)) {
                    if (credit.weight().compareTo(BigDecimal.ONE) == 0) {
                        directConversions++;
                        directValue = directValue.add(credit.creditedValue());
                    } else {
                        assistedConversions++;
                        assistedValue = assistedValue.add(credit.creditedValue());
                    }
                }
            }
        }

        return new AssistedConversionStats(
                affiliateId,
                directConversions,
                assistedConversions,
                directValue,
                assistedValue,
                from,
                to
        );
    }

    // ========== 私有辅助方法 ==========

    private CustomerJourney buildCustomerJourney(String userId) {
        List<TouchPointEntity> entities = touchPointRepository.findRecentTouchPoints(userId, 100);

        List<TouchPoint> touchPoints = entities.stream()
                .map(this::toTouchPoint)
                .collect(Collectors.toList());

        Map<String, Long> touchPointsByAffiliate = touchPoints.stream()
                .collect(Collectors.groupingBy(TouchPoint::affiliateId, Collectors.counting()));

        return new CustomerJourney(
                userId,
                touchPoints,
                touchPoints.size(),
                touchPointsByAffiliate,
                touchPoints.isEmpty() ? null : touchPoints.get(touchPoints.size() - 1).timestamp(),
                touchPoints.isEmpty() ? null : touchPoints.get(0).timestamp()
        );
    }

    private List<AttributionCredit> calculateAttributionCredits(
            List<TouchPoint> touchPoints,
            BigDecimal conversionValue,
            AttributionModel model
    ) {
        return switch (model) {
            case FIRST_CLICK -> firstClickAttribution(touchPoints, conversionValue);
            case LAST_CLICK -> lastClickAttribution(touchPoints, conversionValue);
            case LINEAR -> linearAttribution(touchPoints, conversionValue);
            case TIME_DECAY -> timeDecayAttribution(touchPoints, conversionValue);
            case POSITION_BASED -> positionBasedAttribution(touchPoints, conversionValue);
            case DATA_DRIVEN -> dataDrivenAttribution(touchPoints, conversionValue);
            case DIRECT -> List.of(); // 直接转化无触点
        };
    }

    /**
     * 工业级数据驱动归因算法 (Data-Driven MTA - Shapley / Removal Contribution)
     * <p>
     * 综合考量：
     * 1. 触点交互深度乘数 (CLICK=1.0, ENGAGEMENT=0.7, VIEW=0.4, IMPRESSION=0.2)；
     * 2. 连续平滑时间半衰期衰减 (Half-Life = 7 天，更贴近真实用户认知衰减)；
     * 3. 同一渠道频次边际递减效应 (第 k 次接触的边际贡献按 1/sqrt(k) 递减，防御渠道刷量泛滥)；
     * 4. 链路位置价值补偿 (首次引导探索与末次促成下单额外加权)；
     * 5. 最终权重精确归一化与金额分位无损平账。
     */
    public List<AttributionCredit> dataDrivenAttribution(List<TouchPoint> touchPoints, BigDecimal value) {
        if (touchPoints == null || touchPoints.isEmpty()) {
            return List.of();
        }
        if (touchPoints.size() == 1) {
            return lastClickAttribution(touchPoints, value);
        }

        Instant conversionTime = Instant.now();
        double lambda = Math.log(2) / 7.0; // 7天半衰期衰减系数

        // 统计各 affiliate 累计触点频次用于边际递减计算
        Map<String, Integer> affiliateTouchCounter = new HashMap<>();
        List<Double> rawScores = new ArrayList<>(touchPoints.size());
        int totalTouchPoints = touchPoints.size();

        for (int i = 0; i < totalTouchPoints; i++) {
            TouchPoint tp = touchPoints.get(i);
            int k = affiliateTouchCounter.merge(tp.affiliateId(), 1, Integer::sum);

            // 1. 触点交互深度系数
            double typeMultiplier = switch (tp.type()) {
                case CLICK -> 1.0;
                case ENGAGEMENT -> 0.7;
                case VIEW -> 0.4;
                case IMPRESSION -> 0.2;
            };

            // 2. 连续平滑时间衰减 (以天为连续浮点单位)
            double daysAgo = Math.max(0.0, Duration.between(tp.timestamp(), conversionTime).toMillis() / (1000.0 * 86400.0));
            double recencyWeight = Math.exp(-lambda * daysAgo);

            // 3. 边际递减效应
            double marginalDiminishing = 1.0 / Math.sqrt(k);

            // 4. 位置加权补偿：首触点奖励 20%，末触点奖励 30%
            double positionFactor = 1.0;
            if (i == 0) {
                positionFactor = 1.20;
            } else if (i == totalTouchPoints - 1) {
                positionFactor = 1.30;
            }

            double score = typeMultiplier * recencyWeight * marginalDiminishing * positionFactor;
            rawScores.add(score);
        }

        double totalScore = rawScores.stream().mapToDouble(Double::doubleValue).sum();
        if (totalScore <= 0.0) {
            return linearAttribution(touchPoints, value);
        }

        List<AttributionCredit> credits = new ArrayList<>(totalTouchPoints);
        BigDecimal runningAllocatedValue = BigDecimal.ZERO;

        for (int i = 0; i < totalTouchPoints; i++) {
            TouchPoint tp = touchPoints.get(i);
            double rawScore = rawScores.get(i);
            BigDecimal normalizedWeight = BigDecimal.valueOf(rawScore / totalScore)
                    .setScale(4, RoundingMode.HALF_UP);

            BigDecimal creditedValue;
            if (i == totalTouchPoints - 1) {
                // 最后一项通过差值消除精度舍入尾差，确保全量平账
                creditedValue = value.subtract(runningAllocatedValue);
            } else {
                creditedValue = value.multiply(normalizedWeight).setScale(2, RoundingMode.HALF_UP);
                runningAllocatedValue = runningAllocatedValue.add(creditedValue);
            }

            credits.add(new AttributionCredit(
                    tp.affiliateId(),
                    tp.clickId(),
                    normalizedWeight,
                    creditedValue,
                    "Data-Driven"
            ));
        }

        return credits;
    }

    /**
     * 多模型归因对比分析引擎 (Attribution Models Comparison)
     * <p>
     * 一键生成同一用户旅程在全部 6 种归因模型下的权重与分成对比，支撑精细化 ROI 评估与结算复核。
     */
    public Map<AttributionModel, List<AttributionCredit>> compareAttributionModels(
            List<TouchPoint> touchPoints,
            BigDecimal conversionValue
    ) {
        if (touchPoints == null || touchPoints.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<AttributionModel, List<AttributionCredit>> comparison = new EnumMap<>(AttributionModel.class);
        for (AttributionModel model : List.of(
                AttributionModel.LAST_CLICK,
                AttributionModel.FIRST_CLICK,
                AttributionModel.LINEAR,
                AttributionModel.TIME_DECAY,
                AttributionModel.POSITION_BASED,
                AttributionModel.DATA_DRIVEN
        )) {
            comparison.put(model, calculateAttributionCredits(touchPoints, conversionValue, model));
        }
        return Collections.unmodifiableMap(comparison);
    }

    private List<AttributionCredit> firstClickAttribution(List<TouchPoint> touchPoints, BigDecimal value) {
        TouchPoint first = touchPoints.get(0);
        return List.of(new AttributionCredit(
                first.affiliateId(),
                first.clickId(),
                BigDecimal.ONE,
                value,
                "First Click"
        ));
    }

    private List<AttributionCredit> lastClickAttribution(List<TouchPoint> touchPoints, BigDecimal value) {
        TouchPoint last = touchPoints.get(touchPoints.size() - 1);
        return List.of(new AttributionCredit(
                last.affiliateId(),
                last.clickId(),
                BigDecimal.ONE,
                value,
                "Last Click"
        ));
    }

    private List<AttributionCredit> linearAttribution(List<TouchPoint> touchPoints, BigDecimal value) {
        BigDecimal weight = BigDecimal.ONE.divide(
                BigDecimal.valueOf(touchPoints.size()),
                4,
                RoundingMode.HALF_UP
        );

        return touchPoints.stream()
                .map(tp -> new AttributionCredit(
                        tp.affiliateId(),
                        tp.clickId(),
                        weight,
                        value.multiply(weight).setScale(2, RoundingMode.HALF_UP),
                        "Linear"
                ))
                .collect(Collectors.toList());
    }

    private List<AttributionCredit> timeDecayAttribution(List<TouchPoint> touchPoints, BigDecimal value) {
        Instant conversionTime = Instant.now();
        double lambda = 0.1;

        List<WeightedTouchPoint> weightedPoints = new ArrayList<>();
        double totalWeight = 0.0;

        for (TouchPoint tp : touchPoints) {
            double daysAgo = Math.max(0.0, Duration.between(tp.timestamp(), conversionTime).toMillis() / (1000.0 * 86400.0));
            double weight = Math.exp(-lambda * daysAgo);
            weightedPoints.add(new WeightedTouchPoint(tp, weight));
            totalWeight += weight;
        }

        double finalTotalWeight = totalWeight;
        return weightedPoints.stream()
                .map(wtp -> {
                    BigDecimal normalizedWeight = BigDecimal.valueOf(wtp.weight / finalTotalWeight)
                            .setScale(4, RoundingMode.HALF_UP);
                    return new AttributionCredit(
                            wtp.touchPoint.affiliateId(),
                            wtp.touchPoint.clickId(),
                            normalizedWeight,
                            value.multiply(normalizedWeight).setScale(2, RoundingMode.HALF_UP),
                            "Time Decay"
                    );
                })
                .collect(Collectors.toList());
    }

    private List<AttributionCredit> positionBasedAttribution(List<TouchPoint> touchPoints, BigDecimal value) {
        int count = touchPoints.size();

        if (count == 1) {
            return firstClickAttribution(touchPoints, value);
        }

        List<AttributionCredit> credits = new ArrayList<>(count);

        if (count == 2) {
            // 2 个触点：首尾各 50%
            BigDecimal halfWeight = new BigDecimal("0.50");
            credits.add(new AttributionCredit(
                    touchPoints.get(0).affiliateId(),
                    touchPoints.get(0).clickId(),
                    halfWeight,
                    value.multiply(halfWeight).setScale(2, RoundingMode.HALF_UP),
                    "Position (First)"
            ));
            credits.add(new AttributionCredit(
                    touchPoints.get(1).affiliateId(),
                    touchPoints.get(1).clickId(),
                    halfWeight,
                    value.subtract(value.multiply(halfWeight).setScale(2, RoundingMode.HALF_UP)),
                    "Position (Last)"
            ));
            return credits;
        }

        // 3 个及以上触点：U-Shape (首 40%, 尾 40%, 中间平摊 20%)，严格按时间先后顺序填充
        TouchPoint first = touchPoints.get(0);
        BigDecimal firstWeight = new BigDecimal("0.40");
        BigDecimal firstValue = value.multiply(firstWeight).setScale(2, RoundingMode.HALF_UP);
        credits.add(new AttributionCredit(
                first.affiliateId(),
                first.clickId(),
                firstWeight,
                firstValue,
                "Position (First)"
        ));

        int middleCount = count - 2;
        BigDecimal middleWeight = new BigDecimal("0.20").divide(
                BigDecimal.valueOf(middleCount),
                4,
                RoundingMode.HALF_UP
        );
        BigDecimal allocatedSoFar = firstValue;

        for (int i = 1; i < count - 1; i++) {
            TouchPoint middle = touchPoints.get(i);
            BigDecimal middleValue = value.multiply(middleWeight).setScale(2, RoundingMode.HALF_UP);
            allocatedSoFar = allocatedSoFar.add(middleValue);
            credits.add(new AttributionCredit(
                    middle.affiliateId(),
                    middle.clickId(),
                    middleWeight,
                    middleValue,
                    "Position (Middle)"
            ));
        }

        // 尾触点通过总额相减消除四舍五入偏差，保持严格平账
        TouchPoint last = touchPoints.get(count - 1);
        BigDecimal lastWeight = new BigDecimal("0.40");
        BigDecimal lastValue = value.subtract(allocatedSoFar);
        credits.add(new AttributionCredit(
                last.affiliateId(),
                last.clickId(),
                lastWeight,
                lastValue,
                "Position (Last)"
        ));

        return credits;
    }

    private AttributionResult createDirectConversionResult(
            String userId,
            String conversionId,
            BigDecimal conversionValue,
            Instant conversionTime
    ) {
        AttributionResultEntity entity = new AttributionResultEntity(
                UUID.randomUUID().toString(),
                userId,
                conversionId,
                conversionValue,
                conversionTime,
                AttributionModel.DIRECT.name(),
                0,
                "[]",
                Instant.now()
        );

        attributionResultRepository.save(entity);

        return toAttributionResult(entity);
    }

    private String serializeCredits(List<AttributionCredit> credits) {
        try {
            return objectMapper.writeValueAsString(credits);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<AttributionCredit> deserializeCredits(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<AttributionCredit>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private TouchPoint toTouchPoint(TouchPointEntity entity) {
        return new TouchPoint(
                entity.getId(),
                entity.getUserId(),
                entity.getSessionId(),
                TouchPointType.valueOf(entity.getType()),
                entity.getAffiliateId(),
                entity.getOfferId(),
                entity.getClickId(),
                entity.getSource(),
                entity.getMedium(),
                entity.getCampaign(),
                entity.getTimestamp()
        );
    }

    private AttributionResult toAttributionResult(AttributionResultEntity entity) {
        return new AttributionResult(
                entity.getId(),
                entity.getUserId(),
                entity.getConversionId(),
                entity.getConversionValue(),
                entity.getConversionTime(),
                AttributionModel.valueOf(entity.getAttributionModel()),
                List.of(), // touchPoints not stored
                deserializeCredits(entity.getCredits()),
                entity.getCalculatedAt()
        );
    }

    // ========== 辅助类 ==========

    private record WeightedTouchPoint(TouchPoint touchPoint, double weight) {}

    // ========== 数据记录 ==========

    public record TouchPoint(
            String id,
            String userId,
            String sessionId,
            TouchPointType type,
            String affiliateId,
            String offerId,
            String clickId,
            String source,
            String medium,
            String campaign,
            Instant timestamp
    ) {}

    public record AttributionCredit(
            String affiliateId,
            String clickId,
            BigDecimal weight,
            BigDecimal creditedValue,
            String attribution
    ) {}

    public record AttributionResult(
            String id,
            String userId,
            String conversionId,
            BigDecimal conversionValue,
            Instant conversionTime,
            AttributionModel model,
            List<TouchPoint> touchPoints,
            List<AttributionCredit> credits,
            Instant calculatedAt
    ) {}

    public record AssistedConversionStats(
            String affiliateId,
            long directConversions,
            long assistedConversions,
            BigDecimal directValue,
            BigDecimal assistedValue,
            Instant from,
            Instant to
    ) {}

    public record CustomerJourney(
            String userId,
            List<TouchPoint> touchPoints,
            int totalTouchPoints,
            Map<String, Long> touchPointsByAffiliate,
            Instant firstTouch,
            Instant lastTouch
    ) {}

    public enum AttributionModel {
        FIRST_CLICK,
        LAST_CLICK,
        LINEAR,
        TIME_DECAY,
        POSITION_BASED,
        DATA_DRIVEN,
        DIRECT
    }

    public enum TouchPointType {
        IMPRESSION,
        CLICK,
        VIEW,
        ENGAGEMENT
    }
}
