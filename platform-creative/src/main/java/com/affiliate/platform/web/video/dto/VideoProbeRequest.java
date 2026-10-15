package com.affiliate.platform.web.video.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 视频探针提取请求
 */
public record VideoProbeRequest(
        @NotBlank(message = "videoUrlOrPath cannot be blank")
        String videoUrlOrPath
) {}
