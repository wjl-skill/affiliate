package com.affiliate.platform.web.ad.dto;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.io.Serializable;

/**
 * DSP 竞价质量系数响应
 */
public record DspMultiplierResponse(
        MaterialQualityLevel qualityLevel,
        double qualityMultiplier,
        boolean eligibleForDspBidding
) implements Serializable {}
