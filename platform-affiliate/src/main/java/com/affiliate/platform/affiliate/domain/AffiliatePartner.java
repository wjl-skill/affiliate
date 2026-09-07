package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 联盟营销渠道客实体 (Affiliate Partner Domain Record)
 * <p>
 * 管理网盟下游分发渠道（Publisher/Affiliate）：
 * 包含渠道评级（Tier）、专属 Postback 回传模版、付款账期（Net-7/15/30）与起提金额门槛。
 */
public record AffiliatePartner(
        String id,
        String tenantId,
        @NotBlank String name,
        Status status,
        @NotNull Tier tier,
        String postbackUrlTemplate,
        PaymentTerm paymentTerm,
        BigDecimal minPayoutThreshold,
        Instant createdAt
) {
    public AffiliatePartner {
        status = status == null ? Status.ACTIVE : status;
        tier = tier == null ? Tier.STANDARD : tier;
        paymentTerm = paymentTerm == null ? PaymentTerm.NET_30 : paymentTerm;
        if (minPayoutThreshold == null || minPayoutThreshold.signum() <= 0) {
            minPayoutThreshold = new BigDecimal("100.00"); // 默认 100 美金起提
        }
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum Status {
        /** 正常活动 */
        ACTIVE,
        /** 待审批 */
        PENDING,
        /** 已封禁冻结 */
        SUSPENDED
    }

    public enum Tier {
        /** 标准新手渠道 */
        STANDARD,
        /** 白银渠道 */
        SILVER,
        /** 黄金渠道 */
        GOLD,
        /** VIP 核心大户渠道 */
        VIP
    }

    public enum PaymentTerm {
        /** 周结 (7天账期) */
        NET_7,
        /** 半月结 (15天账期) */
        NET_15,
        /** 月结 (30天账期) */
        NET_30
    }
}
