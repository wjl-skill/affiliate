package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * S2S 转化归因事实实体 (Conversion MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_conversion`。
 */
@TableName("affiliate_conversion")
public class ConversionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String clickId;
    private String offerId;
    private String affiliateId;
    private String transactionId;
    private BigDecimal payout;
    private BigDecimal revenue;
    private String status;
    private Long ctitSeconds;
    private String sub1;
    private String postbackStatus;
    private Instant createdAt;

    public ConversionEntity() {}

    public ConversionEntity(String id, String tenantId, String clickId, String offerId,
                            String affiliateId, String transactionId, BigDecimal payout,
                            BigDecimal revenue, String status, Long ctitSeconds,
                            String sub1, String postbackStatus, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.clickId = clickId;
        this.offerId = offerId;
        this.affiliateId = affiliateId;
        this.transactionId = transactionId;
        this.payout = payout;
        this.revenue = revenue;
        this.status = status;
        this.ctitSeconds = ctitSeconds;
        this.sub1 = sub1;
        this.postbackStatus = postbackStatus;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getClickId() { return clickId; }
    public void setClickId(String clickId) { this.clickId = clickId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public BigDecimal getPayout() { return payout; }
    public void setPayout(BigDecimal payout) { this.payout = payout; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCtitSeconds() { return ctitSeconds; }
    public void setCtitSeconds(Long ctitSeconds) { this.ctitSeconds = ctitSeconds; }

    public String getSub1() { return sub1; }
    public void setSub1(String sub1) { this.sub1 = sub1; }

    public String getPostbackStatus() { return postbackStatus; }
    public void setPostbackStatus(String postbackStatus) { this.postbackStatus = postbackStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
