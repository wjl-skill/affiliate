package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 联盟转化事实实体 (Affiliate Conversion Domain Record)
 * <p>
 * 记录广告主 S2S Postback 成功上报并经由归因对齐后的真实转化：
 * 包含佣金分配（Payout）、广告主应收（Revenue）、CTIT 时间差（用于反作弊审查）及审核生命周期。
 */
public record Conversion(
        String id,
        String tenantId,
        @NotBlank String clickId,
        @NotBlank String txId,
        @NotBlank String offerId,
        @NotBlank String affiliateId,
        @NotNull @DecimalMin("0.00") BigDecimal payout,
        @NotNull @DecimalMin("0.00") BigDecimal revenue,
        BigDecimal saleAmount,
        long ctitSeconds,
        Status status,
        String rejectionReason,
        Instant createdAt
) {
    public Conversion {
        status = status == null ? Status.PENDING : status;
        if (saleAmount == null) saleAmount = BigDecimal.ZERO;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum Status {
        /** 待审核（处于无理由退单缓冲期） */
        PENDING,
        /** 已审核通过，可进入结算流水 */
        APPROVED,
        /** 审核驳回（退款退货或核销失败） */
        REJECTED,
        /** 疑似欺诈（CTIT 异常或黑名单命中） */
        FRAUD_SUSPECTED
    }

    /**
     * 审核通过转化
     */
    public Conversion approve() {
        return new Conversion(id, tenantId, clickId, txId, offerId, affiliateId, payout, revenue, saleAmount, ctitSeconds, Status.APPROVED, null, createdAt);
    }

    /**
     * 审核驳回转化
     */
    public Conversion reject(String reason) {
        return new Conversion(id, tenantId, clickId, txId, offerId, affiliateId, payout, revenue, saleAmount, ctitSeconds, Status.REJECTED, reason, createdAt);
    }
}
