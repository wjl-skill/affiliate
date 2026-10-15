package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 统一异步生成任务记录持久化实体
 */
@TableName("creative_ai_task_record")
public class CreativeAiTaskRecordEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String taskNo;
    private String generateType;
    private String modelCode;
    private String providerCode;
    private String remoteTaskId;
    private String status;
    private String prompt;

    @TableField("input_assets_json")
    private String inputAssetsJson; // JSONB

    private String rawOutputUrl;
    private String ossUrl;
    private Integer durationSeconds;
    private String usageUnit;
    private BigDecimal usageQuantity;
    private BigDecimal estimatedCost;
    private String costStatus;
    private String errorCode;
    private String errorMessage;
    private String createdBy;
    private Instant createdAt;
    private Instant finishedAt;

    public CreativeAiTaskRecordEntity() {}

    public CreativeAiTaskRecordEntity(String id, String tenantId, String taskNo, String generateType, String modelCode,
                                      String providerCode, String remoteTaskId, String status, String prompt,
                                      String inputAssetsJson, String rawOutputUrl, String ossUrl, Integer durationSeconds,
                                      String usageUnit, BigDecimal usageQuantity, BigDecimal estimatedCost,
                                      String costStatus, String errorCode, String errorMessage, String createdBy,
                                      Instant createdAt, Instant finishedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.taskNo = taskNo;
        this.generateType = generateType;
        this.modelCode = modelCode;
        this.providerCode = providerCode;
        this.remoteTaskId = remoteTaskId;
        this.status = status;
        this.prompt = prompt;
        this.inputAssetsJson = inputAssetsJson;
        this.rawOutputUrl = rawOutputUrl;
        this.ossUrl = ossUrl;
        this.durationSeconds = durationSeconds;
        this.usageUnit = usageUnit;
        this.usageQuantity = usageQuantity;
        this.estimatedCost = estimatedCost;
        this.costStatus = costStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getGenerateType() { return generateType; }
    public void setGenerateType(String generateType) { this.generateType = generateType; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getRemoteTaskId() { return remoteTaskId; }
    public void setRemoteTaskId(String remoteTaskId) { this.remoteTaskId = remoteTaskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getInputAssetsJson() { return inputAssetsJson; }
    public void setInputAssetsJson(String inputAssetsJson) { this.inputAssetsJson = inputAssetsJson; }
    public String getRawOutputUrl() { return rawOutputUrl; }
    public void setRawOutputUrl(String rawOutputUrl) { this.rawOutputUrl = rawOutputUrl; }
    public String getOssUrl() { return ossUrl; }
    public void setOssUrl(String ossUrl) { this.ossUrl = ossUrl; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getUsageUnit() { return usageUnit; }
    public void setUsageUnit(String usageUnit) { this.usageUnit = usageUnit; }
    public BigDecimal getUsageQuantity() { return usageQuantity; }
    public void setUsageQuantity(BigDecimal usageQuantity) { this.usageQuantity = usageQuantity; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    public String getCostStatus() { return costStatus; }
    public void setCostStatus(String costStatus) { this.costStatus = costStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
