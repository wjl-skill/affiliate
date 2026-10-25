package com.affiliate.platform.service.ad.impl;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.entity.CreativeAdLearningRuleEntity;
import com.affiliate.platform.mapper.CreativeAdLearningRuleMapper;
import com.affiliate.platform.service.ad.AdLearningRuleEngine;
import com.affiliate.platform.service.ad.AdLearningStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 广告学习期规则判定引擎实现 (支持 PostgreSQL 持久化与内存双模存储)
 */
@Service
public class AdLearningRuleEngineImpl implements AdLearningRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(AdLearningRuleEngineImpl.class);

    public static final int DEFAULT_TIME_DAYS = 2; // 48 小时
    public static final BigDecimal DEFAULT_COST_THRESHOLD = BigDecimal.valueOf(50.00);

    private final CreativeAdLearningRuleMapper learningRuleMapper;
    private final Map<String, CreativeAdLearningRuleEntity> ruleStore = new ConcurrentHashMap<>();

    public AdLearningRuleEngineImpl() {
        this(null);
    }

    @Autowired
    public AdLearningRuleEngineImpl(@Autowired(required = false) CreativeAdLearningRuleMapper learningRuleMapper) {
        this.learningRuleMapper = learningRuleMapper;
    }

    @Override
    public void registerLearningExperiment(
            String tenantId,
            String entityId,
            String entityName,
            String channelCode
    ) {
        if (entityId == null || entityId.isBlank()) {
            return;
        }
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        String id = "lrn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant now = Instant.now();

        CreativeAdLearningRuleEntity entity = new CreativeAdLearningRuleEntity(
                id,
                effectiveTenant,
                "AD_GROUP",
                entityId,
                entityName != null ? entityName : entityId,
                channelCode != null ? channelCode : "TIKTOK",
                DEFAULT_TIME_DAYS,
                DEFAULT_COST_THRESHOLD,
                now,
                BigDecimal.ZERO,
                true,
                true,
                now,
                now
        );
        ruleStore.put(effectiveTenant + ":" + entityId, entity);
        if (learningRuleMapper != null) {
            try {
                learningRuleMapper.insert(entity);
            } catch (Exception e) {
                log.debug("[AdLearningRuleEngine] Failed to insert learning rule: {}", e.getMessage());
            }
        }
    }

    @Override
    public AdLearningStatus evaluateLearningPhase(
            Instant firstMetricTime,
            BigDecimal cumulativeCost,
            MaterialQualityLevel qualityLevel,
            Integer timeDaysThreshold,
            BigDecimal costThreshold
    ) {
        if (firstMetricTime == null) {
            return new AdLearningStatus(false, false, false, "广告尚未产生投放指标数据，学习期未开始");
        }

        int days = timeDaysThreshold != null && timeDaysThreshold > 0 ? timeDaysThreshold : DEFAULT_TIME_DAYS;
        BigDecimal maxCost = costThreshold != null ? costThreshold : DEFAULT_COST_THRESHOLD;
        BigDecimal cost = cumulativeCost != null ? cumulativeCost : BigDecimal.ZERO;

        Instant expireAt = firstMetricTime.plus(days, ChronoUnit.DAYS);
        Instant now = Instant.now();

        boolean withinTimeWindow = now.isBefore(expireAt);
        boolean withinCostThreshold = cost.compareTo(maxCost) <= 0;

        boolean isLearning = withinTimeWindow && withinCostThreshold;

        if (isLearning) {
            if (qualityLevel == MaterialQualityLevel.BLACKHOLE) {
                return new AdLearningStatus(true, true, true,
                        "处于学习期但已命中黑洞素材(BLACKHOLE)，触发自动熔断下线预警");
            }
            return new AdLearningStatus(true, true, false,
                    String.format("处于模型探索学习期内 (剩余时间约 %d 小时，累计消耗 $%.2f/$%.2f)，锁死出价禁止人工调价",
                            Math.max(0, ChronoUnit.HOURS.between(now, expireAt)), cost, maxCost));
        }

        String exitReason = !withinTimeWindow
                ? "已超过 48 小时学习期时间窗口，已平稳度过学习期"
                : String.format("累计消耗 $%.2f 已达门槛 $%.2f，算法模型探索完成度过学习期", cost, maxCost);

        return new AdLearningStatus(false, false, false, exitReason);
    }
}
