package com.affiliate.platform.web.ad.dto;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Affiliate SmartLink 路由综合得分请求
 */
public record SmartLinkScoreRequest(
        BigDecimal epc,
        double capFactor,
        MaterialQualityLevel qualityLevel
) implements Serializable {}
