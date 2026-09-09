package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 推荐关系实体
 */
@Entity
@Table(name = "affiliate_referral_relationship", indexes = {
        @Index(name = "idx_referral_referee", columnList = "referee_id", unique = true),
        @Index(name = "idx_referral_referrer", columnList = "referrer_id"),
        @Index(name = "idx_referral_code", columnList = "referral_code"),
        @Index(name = "idx_referral_status", columnList = "status")
})
@com.baomidou.mybatisplus.annotation.TableName("affiliate_referral_relationship")
public class ReferralRelationshipEntity {

    @Id
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "referee_id", nullable = false, unique = true, length = 64)
    private String refereeId; // 被推荐人（下线）

    @Column(name = "referrer_id", nullable = false, length = 64)
    private String referrerId; // 推荐人（上线）

    @Column(name = "referral_code", nullable = false, length = 128)
    private String referralCode;

    @Column(name = "tier", nullable = false)
    private Integer tier; // 推荐层级（1=直接下线，2=二级下线）

    @Column(name = "status", nullable = false, length = 20)
    private String status; // ACTIVE, PAUSED, TERMINATED

    @Column(name = "total_commission_earned", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCommissionEarned;

    @Column(name = "total_conversions", nullable = false)
    private Long totalConversions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_conversion_at")
    private Instant lastConversionAt;

    // Constructors
    public ReferralRelationshipEntity() {
    }

    public ReferralRelationshipEntity(String id, String refereeId, String referrerId,
                                     String referralCode, Integer tier, String status,
                                     BigDecimal totalCommissionEarned, Long totalConversions,
                                     Instant createdAt, Instant lastConversionAt) {
        this.id = id;
        this.refereeId = refereeId;
        this.referrerId = referrerId;
        this.referralCode = referralCode;
        this.tier = tier;
        this.status = status;
        this.totalCommissionEarned = totalCommissionEarned;
        this.totalConversions = totalConversions;
        this.createdAt = createdAt;
        this.lastConversionAt = lastConversionAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRefereeId() {
        return refereeId;
    }

    public void setRefereeId(String refereeId) {
        this.refereeId = refereeId;
    }

    public String getReferrerId() {
        return referrerId;
    }

    public void setReferrerId(String referrerId) {
        this.referrerId = referrerId;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public Integer getTier() {
        return tier;
    }

    public void setTier(Integer tier) {
        this.tier = tier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalCommissionEarned() {
        return totalCommissionEarned;
    }

    public void setTotalCommissionEarned(BigDecimal totalCommissionEarned) {
        this.totalCommissionEarned = totalCommissionEarned;
    }

    public Long getTotalConversions() {
        return totalConversions;
    }

    public void setTotalConversions(Long totalConversions) {
        this.totalConversions = totalConversions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastConversionAt() {
        return lastConversionAt;
    }

    public void setLastConversionAt(Instant lastConversionAt) {
        this.lastConversionAt = lastConversionAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
