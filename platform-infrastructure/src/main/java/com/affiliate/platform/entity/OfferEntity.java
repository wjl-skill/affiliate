package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 网盟推广计划持久化实体 (Offer MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_offer`。
 */
@TableName("affiliate_offer")
public class OfferEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String advertiserId;
    private String title;
    private String landingPageUrl;
    private String payoutType;
    private BigDecimal defaultPayout;
    private BigDecimal defaultRevenue;
    private String status;
    private Integer dailyConversionCap;
    private BigDecimal dailyRevenueCap;
    private String fallbackOfferId;
    private Instant expiresAt;
    private Instant createdAt;

    public OfferEntity() {}

    public OfferEntity(String id, String tenantId, String advertiserId, String title,
                       String landingPageUrl, String payoutType, BigDecimal defaultPayout,
                       BigDecimal defaultRevenue, String status, Integer dailyConversionCap,
                       BigDecimal dailyRevenueCap, String fallbackOfferId, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.advertiserId = advertiserId;
        this.title = title;
        this.landingPageUrl = landingPageUrl;
        this.payoutType = payoutType;
        this.defaultPayout = defaultPayout;
        this.defaultRevenue = defaultRevenue;
        this.status = status;
        this.dailyConversionCap = dailyConversionCap;
        this.dailyRevenueCap = dailyRevenueCap;
        this.fallbackOfferId = fallbackOfferId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAdvertiserId() { return advertiserId; }
    public void setAdvertiserId(String advertiserId) { this.advertiserId = advertiserId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLandingPageUrl() { return landingPageUrl; }
    public void setLandingPageUrl(String landingPageUrl) { this.landingPageUrl = landingPageUrl; }

    public String getPayoutType() { return payoutType; }
    public void setPayoutType(String payoutType) { this.payoutType = payoutType; }

    public BigDecimal getDefaultPayout() { return defaultPayout; }
    public void setDefaultPayout(BigDecimal defaultPayout) { this.defaultPayout = defaultPayout; }

    public BigDecimal getDefaultRevenue() { return defaultRevenue; }
    public void setDefaultRevenue(BigDecimal defaultRevenue) { this.defaultRevenue = defaultRevenue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getDailyConversionCap() { return dailyConversionCap; }
    public void setDailyConversionCap(Integer dailyConversionCap) { this.dailyConversionCap = dailyConversionCap; }

    public BigDecimal getDailyRevenueCap() { return dailyRevenueCap; }
    public void setDailyRevenueCap(BigDecimal dailyRevenueCap) { this.dailyRevenueCap = dailyRevenueCap; }

    public String getFallbackOfferId() { return fallbackOfferId; }
    public void setFallbackOfferId(String fallbackOfferId) { this.fallbackOfferId = fallbackOfferId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
