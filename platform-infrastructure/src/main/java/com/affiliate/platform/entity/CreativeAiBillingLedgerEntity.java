package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI 算力消耗计费分录实体
 */
@TableName("creative_ai_billing_ledger")
public class CreativeAiBillingLedgerEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String taskId;
    private String departmentId;
    private String projectId;
    private String modelCode;
    private BigDecimal deductedAmount;
    private BigDecimal walletBalanceAfter;
    private String idempotentKey;
    private Instant settledAt;

    public CreativeAiBillingLedgerEntity() {}

    public CreativeAiBillingLedgerEntity(String id, String tenantId, String taskId, String departmentId,
                                         String projectId, String modelCode, BigDecimal deductedAmount,
                                         BigDecimal walletBalanceAfter, String idempotentKey, Instant settledAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.taskId = taskId;
        this.departmentId = departmentId;
        this.projectId = projectId;
        this.modelCode = modelCode;
        this.deductedAmount = deductedAmount;
        this.walletBalanceAfter = walletBalanceAfter;
        this.idempotentKey = idempotentKey;
        this.settledAt = settledAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public BigDecimal getDeductedAmount() { return deductedAmount; }
    public void setDeductedAmount(BigDecimal deductedAmount) { this.deductedAmount = deductedAmount; }
    public BigDecimal getWalletBalanceAfter() { return walletBalanceAfter; }
    public void setWalletBalanceAfter(BigDecimal walletBalanceAfter) { this.walletBalanceAfter = walletBalanceAfter; }
    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
}
