package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 推广计划多事件目标领域模型 (Offer Event Goal Domain Record)
 * <p>
 * 支撑工业级网盟（Affise / Everflow）的多层级转化漏斗：
 * 广告主可在单个 Offer 下定义多个不同业务深度的转化事件（如 INSTALL, REGISTRATION, FIRST_DEPOSIT, PURCHASE），
 * 每个事件目标具备独立的佣金（Payout）、应收（Revenue）及独立的上限配额控制。
 */
public record OfferGoal(
        @NotBlank String goalId,
        @NotBlank String offerId,
        @NotBlank String name,
        @NotNull EventType eventType,
        @NotNull @DecimalMin("0.00") BigDecimal payout,
        @NotNull @DecimalMin("0.00") BigDecimal revenue,
        boolean isDefault,
        boolean contributesToCap,
        int windowDays
) {
    public OfferGoal {
        if (payout == null) payout = BigDecimal.ZERO;
        if (revenue == null) revenue = BigDecimal.ZERO;
        if (windowDays <= 0) windowDays = 30; // 默认 30 天后置归因窗口
    }

    public enum EventType {
        /** 首包安装 */
        INSTALL,
        /** 用户激活注册 */
        REGISTRATION,
        /** 首冲或首单付费 */
        FIRST_DEPOSIT,
        /** 再次购买 / 复购 */
        PURCHASE,
        /** 自定义后置深度漏斗行为 */
        CUSTOM
    }
}
