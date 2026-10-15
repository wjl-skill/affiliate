package com.affiliate.platform.web.studio.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建长视频工作台项目请求
 */
public record StudioProjectCreateRequest(
        @NotBlank(message = "title cannot be blank")
        String title,

        String brief,
        String aspectRatio,
        Integer durationSeconds,
        String targetPlatform,
        String adType,
        String creativeStyle,
        String modelCode
) {}
