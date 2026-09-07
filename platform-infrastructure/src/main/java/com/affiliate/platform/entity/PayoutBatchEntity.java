package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 出海批量打款结算批次持久化主实体 (Payout Batch Header MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `billing_payout_batch`。
 */
@TableName("billing_payout_batch")
public class PayoutBatchEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String batchNumber;
    private String paymentMethod;
    private String status; // 'DRAFT', 'PROCESSING', 'DISBURSED', 'FAILED'
    private Integer itemCount;
    private BigDecimal totalGrossUsd;
    private BigDecimal totalTaxUsd;
    private BigDecimal totalNetUsd;
    private Instant disbursedAt;
    private Instant createdAt;

    public PayoutBatchEntity() {}

    public PayoutBatchEntity(String id, String tenantId, String batchNumber, String paymentMethod,
                             String status, Integer itemCount, BigDecimal totalGrossUsd,
                             BigDecimal totalTaxUsd, BigDecimal totalNetUsd, Instant disbursedAt, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.batchNumber = batchNumber;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.itemCount = itemCount;
        this.totalGrossUsd = totalGrossUsd;
        this.totalTaxUsd = totalTaxUsd;
        this.totalNetUsd = totalNetUsd;
        this.disbursedAt = disbursedAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public BigDecimal getTotalGrossUsd() { return totalGrossUsd; }
    public void setTotalGrossUsd(BigDecimal totalGrossUsd) { this.totalGrossUsd = totalGrossUsd; }

    public BigDecimal getTotalTaxUsd() { return totalTaxUsd; }
    public void setTotalTaxUsd(BigDecimal totalTaxUsd) { this.totalTaxUsd = totalTaxUsd; }

    public BigDecimal getTotalNetUsd() { return totalNetUsd; }
    public void setTotalNetUsd(BigDecimal totalNetUsd) { this.totalNetUsd = totalNetUsd; }

    public Instant getDisbursedAt() { return disbursedAt; }
    public void setDisbursedAt(Instant disbursedAt) { this.disbursedAt = disbursedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
