package com.affiliate.platform.web.studio.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 数字人主播形象注册/保存请求
 */
public record AvatarRegisterRequest(
        @NotBlank(message = "avatarName cannot be blank")
        String avatarName,

        @NotBlank(message = "originalImageUrl cannot be blank")
        String originalImageUrl,

        String maskImageUrl,

        String speakerCode
) implements Serializable {}
