package com.affiliate.platform.web.ad.dto;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 广告学习期判定请求
 */
public record AdLearningEvaluationRequest(
        Instant firstMetricTime,
        BigDecimal cumulativeCost,
        MaterialQualityLevel qualityLevel,
        Integer timeDaysThreshold,
        BigDecimal costThreshold
) implements Serializable {}
