package com.affiliate.platform.service.ad.impl;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.service.ad.AdLearningRuleEngine;
import com.affiliate.platform.service.ad.AdLearningStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 广告学习期规则判定引擎实现
 */
@Service
public class AdLearningRuleEngineImpl implements AdLearningRuleEngine {

    public static final int DEFAULT_TIME_DAYS = 2; // 48 小时
    public static final BigDecimal DEFAULT_COST_THRESHOLD = BigDecimal.valueOf(50.00);

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
