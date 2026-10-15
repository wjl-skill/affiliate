package com.affiliate.platform.web.studio.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 数字人唇形渲染驱动请求
 */
public record DigitalHumanDriveRequest(
        String avatarId,

        @NotBlank(message = "voiceoverText cannot be blank")
        String voiceoverText,

        String speaker,
        String speed
) {}
