package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI 视频模型元数据实体
 */
@TableName("creative_ai_model_meta")
public class CreativeAiModelMetaEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String modelCode;
    private String modelName;
    private String providerCode;

    @TableField("supported_types")
    private String supportedTypes; // JSONB

    @TableField("field_schema")
    private String fieldSchema; // JSONB

    private String billingUnit;
    private BigDecimal unitPrice;
    private BigDecimal pricingMultiplier;
    private Boolean enabled;
    private Instant createdAt;

    public CreativeAiModelMetaEntity() {}

    public CreativeAiModelMetaEntity(String id, String tenantId, String modelCode, String modelName, String providerCode,
                                     String supportedTypes, String fieldSchema, String billingUnit,
                                     BigDecimal unitPrice, BigDecimal pricingMultiplier, Boolean enabled, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.modelCode = modelCode;
        this.modelName = modelName;
        this.providerCode = providerCode;
        this.supportedTypes = supportedTypes;
        this.fieldSchema = fieldSchema;
        this.billingUnit = billingUnit;
        this.unitPrice = unitPrice;
        this.pricingMultiplier = pricingMultiplier;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getSupportedTypes() { return supportedTypes; }
    public void setSupportedTypes(String supportedTypes) { this.supportedTypes = supportedTypes; }
    public String getFieldSchema() { return fieldSchema; }
    public void setFieldSchema(String fieldSchema) { this.fieldSchema = fieldSchema; }
    public String getBillingUnit() { return billingUnit; }
    public void setBillingUnit(String billingUnit) { this.billingUnit = billingUnit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getPricingMultiplier() { return pricingMultiplier; }
    public void setPricingMultiplier(BigDecimal pricingMultiplier) { this.pricingMultiplier = pricingMultiplier; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
