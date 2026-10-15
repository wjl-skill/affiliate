package com.affiliate.platform.web.ad.dto;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Affiliate SmartLink 路由综合得分响应
 */
public record SmartLinkScoreResponse(
        BigDecimal epc,
        double capFactor,
        MaterialQualityLevel qualityLevel,
        BigDecimal routingScore
) implements Serializable {}
