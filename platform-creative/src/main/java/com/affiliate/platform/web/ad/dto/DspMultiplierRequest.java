package com.affiliate.platform.web.ad.dto;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.io.Serializable;

/**
 * DSP 竞价质量系数请求
 */
public record DspMultiplierRequest(
        MaterialQualityLevel qualityLevel
) implements Serializable {}
