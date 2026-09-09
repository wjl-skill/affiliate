package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 推荐佣金流水实体
 */
@Entity
@Table(name = "affiliate_referral_commission", indexes = {
        @Index(name = "idx_ref_commission_referrer", columnList = "referrer_id"),
        @Index(name = "idx_ref_commission_referee", columnList = "referee_id"),
        @Index(name = "idx_ref_commission_conversion", columnList = "conversion_id"),
        @Index(name = "idx_ref_commission_status", columnList = "status"),
        @Index(name = "idx_ref_commission_created", columnList = "created_at")
})
@com.baomidou.mybatisplus.annotation.TableName("affiliate_referral_commission")
public class ReferralCommissionEntity {

    @Id
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "referrer_id", nullable = false, length = 64)
    private String referrerId; // 推荐人

    @Column(name = "referee_id", nullable = false, length = 64)
    private String refereeId; // 被推荐人

    @Column(name = "conversion_id", nullable = false, length = 64)
    private String conversionId; // 关联的转化ID

    @Column(name = "tier", nullable = false)
    private Integer tier; // 推荐层级（1或2）

    @Column(name = "commission", nullable = false, precision = 12, scale = 2)
    private BigDecimal commission; // 推荐佣金金额

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount; // 原始转化佣金

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, APPROVED, REJECTED, PAID

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    // Constructors
    public ReferralCommissionEntity() {
    }

    public ReferralCommissionEntity(String id, String referrerId, String refereeId,
                                   String conversionId, Integer tier, BigDecimal commission,
                                   BigDecimal baseAmount, String status, Instant createdAt,
                                   Instant processedAt) {
        this.id = id;
        this.referrerId = referrerId;
        this.refereeId = refereeId;
        this.conversionId = conversionId;
        this.tier = tier;
        this.commission = commission;
        this.baseAmount = baseAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferrerId() {
        return referrerId;
    }

    public void setReferrerId(String referrerId) {
        this.referrerId = referrerId;
    }

    public String getRefereeId() {
        return refereeId;
    }

    public void setRefereeId(String refereeId) {
        this.refereeId = refereeId;
    }

    public String getConversionId() {
        return conversionId;
    }

    public void setConversionId(String conversionId) {
        this.conversionId = conversionId;
    }

    public Integer getTier() {
        return tier;
    }

    public void setTier(Integer tier) {
        this.tier = tier;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
