package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 租户大模型调用配置持久化实体 (AI Model Config Entity)
 */
@TableName("ai_model_config")
public class AiModelConfigEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String name;
    private String provider;
    private String modelName;
    private String apiKeyEncrypted;
    private String baseUrl;
    private BigDecimal temperature;
    private Integer maxTokens;
    private BigDecimal topP;
    private Integer timeoutSeconds;
    private Boolean isDefault;
    private Boolean active;
    private Integer priority;
    private Instant createdAt;
    private Instant updatedAt;

    public AiModelConfigEntity() {}

    public AiModelConfigEntity(String id, String tenantId, String name, String provider, String modelName,
                              String apiKeyEncrypted, String baseUrl, BigDecimal temperature, Integer maxTokens,
                              BigDecimal topP, Integer timeoutSeconds, Boolean isDefault, Boolean active,
                              Integer priority, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.provider = provider;
        this.modelName = modelName;
        this.apiKeyEncrypted = apiKeyEncrypted;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.timeoutSeconds = timeoutSeconds;
        this.isDefault = isDefault;
        this.active = active;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getApiKeyEncrypted() { return apiKeyEncrypted; }
    public void setApiKeyEncrypted(String apiKeyEncrypted) { this.apiKeyEncrypted = apiKeyEncrypted; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public BigDecimal getTopP() { return topP; }
    public void setTopP(BigDecimal topP) { this.topP = topP; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
