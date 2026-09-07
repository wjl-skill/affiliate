package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 渠道专属阶梯出价实体 (Offer Tier Payout Entity)
 * <p>
 * 映射数据库表 `affiliate_offer_tier_payout`。
 */
@TableName("affiliate_offer_tier_payout")
public class OfferTierPayoutEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String offerId;
    private String affiliateId;
    private String targetTier;
    private BigDecimal customPayout;
    private BigDecimal customRevenue;
    private Instant createdAt;

    public OfferTierPayoutEntity() {}

    public OfferTierPayoutEntity(Long id, String offerId, String affiliateId, String targetTier,
                                 BigDecimal customPayout, BigDecimal customRevenue, Instant createdAt) {
        this.id = id;
        this.offerId = offerId;
        this.affiliateId = affiliateId;
        this.targetTier = targetTier;
        this.customPayout = customPayout;
        this.customRevenue = customRevenue;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getTargetTier() { return targetTier; }
    public void setTargetTier(String targetTier) { this.targetTier = targetTier; }

    public BigDecimal getCustomPayout() { return customPayout; }
    public void setCustomPayout(BigDecimal customPayout) { this.customPayout = customPayout; }

    public BigDecimal getCustomRevenue() { return customRevenue; }
    public void setCustomRevenue(BigDecimal customRevenue) { this.customRevenue = customRevenue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
