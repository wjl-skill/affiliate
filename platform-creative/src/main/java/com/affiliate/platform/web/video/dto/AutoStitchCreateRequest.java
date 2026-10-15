package com.affiliate.platform.web.video.dto;

import com.affiliate.platform.domain.video.AlignMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 批量混剪配置创建请求
 */
public record AutoStitchCreateRequest(
        @NotBlank(message = "configName cannot be blank")
        String configName,

        @NotEmpty(message = "introVideoUrls cannot be empty")
        List<String> introVideoUrls,

        @NotEmpty(message = "bodyVideoUrls cannot be empty")
        List<String> bodyVideoUrls,

        String targetAspectRatio,
        AlignMode alignMode,
        Map<String, Object> ctaConfig,
        Instant scheduledTime
) {}
