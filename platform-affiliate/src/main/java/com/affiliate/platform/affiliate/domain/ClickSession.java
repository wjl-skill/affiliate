package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * 点击追踪会话存根 (Click Session Record)
 * <p>
 * 存储渠道推广链接被点击时的原始上下文：
 * 包含全局加密密码级唯一 `click_id`、多级子渠道参数 `sub1`~`sub5` 及访客网络环境，
 * 用于后续 S2S Postback 转化时完成精准归因对齐。
 */
public record ClickSession(
        @NotBlank String clickId,
        String tenantId,
        @NotBlank String offerId,
        @NotBlank String affiliateId,
        String sub1,
        String sub2,
        String sub3,
        String sub4,
        String sub5,
        String ip,
        String userAgent,
        String country,
        int deviceType,
        Instant createdAt,
        Instant expiresAt
) {
    public ClickSession {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        // 默认 30 天归因窗口 (Attribution Window)
        if (expiresAt == null) {
            expiresAt = createdAt.plusSeconds(30L * 24 * 3600);
        }
    }

    /**
     * 判断点击会话在指定时间是否仍处于合法归因窗口内
     */
    public boolean isValid(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
