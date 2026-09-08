package com.affiliate.platform.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Offer阶梯出价实体（渠道或等级专属定价）
 */
@Entity
@Table(
        name = "affiliate_offer_tier_payout",
        indexes = {
                @Index(name = "idx_offer_tier_payout_offer_id", columnList = "offer_id"),
                @Index(name = "idx_offer_tier_payout_affiliate_id", columnList = "affiliate_id"),
                @Index(name = "idx_offer_tier_payout_target_tier", columnList = "target_tier")
        }
)
public class OfferTierPayoutEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "affiliate_id", length = 64)
    private String affiliateId;

    @Column(name = "target_tier", length = 32)
    private String targetTier;

    @Column(name = "custom_payout", precision = 12, scale = 4)
    private BigDecimal customPayout;

    @Column(name = "custom_revenue", precision = 12, scale = 4)
    private BigDecimal customRevenue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OfferTierPayoutEntity() {
    }

    public OfferTierPayoutEntity(
            String id,
            String offerId,
            String affiliateId,
            String targetTier,
            BigDecimal customPayout,
            BigDecimal customRevenue,
            Instant createdAt
    ) {
        this.id = id;
        this.offerId = offerId;
        this.affiliateId = affiliateId;
        this.targetTier = targetTier;
        this.customPayout = customPayout;
        this.customRevenue = customRevenue;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    public String getTargetTier() {
        return targetTier;
    }

    public void setTargetTier(String targetTier) {
        this.targetTier = targetTier;
    }

    public BigDecimal getCustomPayout() {
        return customPayout;
    }

    public void setCustomPayout(BigDecimal customPayout) {
        this.customPayout = customPayout;
    }

    public BigDecimal getCustomRevenue() {
        return customRevenue;
    }

    public void setCustomRevenue(BigDecimal customRevenue) {
        this.customRevenue = customRevenue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
