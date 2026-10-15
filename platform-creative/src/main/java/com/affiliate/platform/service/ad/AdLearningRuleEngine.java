package com.affiliate.platform.service.ad;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 广告学习期规则判定引擎
 */
public interface AdLearningRuleEngine {

    /**
     * 根据首次产生指标时间、累计消耗、当前素材质量标签判定学习期状态
     */
    AdLearningStatus evaluateLearningPhase(
            Instant firstMetricTime,
            BigDecimal cumulativeCost,
            MaterialQualityLevel qualityLevel,
            Integer timeDaysThreshold,
            BigDecimal costThreshold
    );
}
