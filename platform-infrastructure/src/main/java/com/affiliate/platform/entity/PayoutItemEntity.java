package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 出海批量打款明细清单从表持久化实体 (Payout Item Line MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `billing_payout_item`。
 */
@TableName("billing_payout_item")
public class PayoutItemEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String batchId;
    private String tenantId;
    private String affiliateId;
    private String beneficiaryName;
    private String taxId;
    private BigDecimal taxRate;
    private BigDecimal grossUsd;
    private BigDecimal taxUsd;
    private String currency;
    private BigDecimal fxRate;
    private BigDecimal targetAmount;
    private String method;
    private String account;
    private String status; // 'PENDING', 'PAID', 'FAILED'
    private Instant createdAt;

    public PayoutItemEntity() {}

    public PayoutItemEntity(String id, String batchId, String tenantId, String affiliateId,
                            String beneficiaryName, String taxId, BigDecimal taxRate,
                            BigDecimal grossUsd, BigDecimal taxUsd, String currency,
                            BigDecimal fxRate, BigDecimal targetAmount, String method,
                            String account, String status, Instant createdAt) {
        this.id = id;
        this.batchId = batchId;
        this.tenantId = tenantId;
        this.affiliateId = affiliateId;
        this.beneficiaryName = beneficiaryName;
        this.taxId = taxId;
        this.taxRate = taxRate;
        this.grossUsd = grossUsd;
        this.taxUsd = taxUsd;
        this.currency = currency;
        this.fxRate = fxRate;
        this.targetAmount = targetAmount;
        this.method = method;
        this.account = account;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public BigDecimal getGrossUsd() { return grossUsd; }
    public void setGrossUsd(BigDecimal grossUsd) { this.grossUsd = grossUsd; }

    public BigDecimal getTaxUsd() { return taxUsd; }
    public void setTaxUsd(BigDecimal taxUsd) { this.taxUsd = taxUsd; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }

    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
