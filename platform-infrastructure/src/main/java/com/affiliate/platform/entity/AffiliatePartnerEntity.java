package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 联盟营销渠道客持久化实体 (Affiliate Partner MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_partner`。
 */
@TableName("affiliate_partner")
public class AffiliatePartnerEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String name;
    private String status;
    private String tier;
    private String postbackUrlTemplate;
    private String paymentTerm;
    private BigDecimal minPayoutThreshold;
    private Instant createdAt;
    private Instant updatedAt;

    public AffiliatePartnerEntity() {}

    public AffiliatePartnerEntity(String id, String tenantId, String name, String status,
                                  String tier, String postbackUrlTemplate, String paymentTerm,
                                  BigDecimal minPayoutThreshold, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.status = status;
        this.tier = tier;
        this.postbackUrlTemplate = postbackUrlTemplate;
        this.paymentTerm = paymentTerm;
        this.minPayoutThreshold = minPayoutThreshold;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getPostbackUrlTemplate() { return postbackUrlTemplate; }
    public void setPostbackUrlTemplate(String postbackUrlTemplate) { this.postbackUrlTemplate = postbackUrlTemplate; }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }

    public BigDecimal getMinPayoutThreshold() { return minPayoutThreshold; }
    public void setMinPayoutThreshold(BigDecimal minPayoutThreshold) { this.minPayoutThreshold = minPayoutThreshold; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
