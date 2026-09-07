package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 渠道财务结算发票实体 (Affiliate Invoice MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_invoice`。
 */
@TableName("affiliate_invoice")
public class AffiliateInvoiceEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String affiliateId;
    private String billingCycle;
    private BigDecimal amount;
    private String status;
    private String paymentTerm;
    private Instant createdAt;
    private Instant paidAt;

    public AffiliateInvoiceEntity() {}

    public AffiliateInvoiceEntity(String id, String tenantId, String affiliateId,
                                  String billingCycle, BigDecimal amount, String status,
                                  String paymentTerm, Instant createdAt, Instant paidAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.affiliateId = affiliateId;
        this.billingCycle = billingCycle;
        this.amount = amount;
        this.status = status;
        this.paymentTerm = paymentTerm;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
