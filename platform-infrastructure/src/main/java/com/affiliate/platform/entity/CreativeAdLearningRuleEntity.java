package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 广告学习期监控配置与状态实体
 * <p>
 * 映射数据库表 `creative_ad_learning_rule`。
 */
@TableName("creative_ad_learning_rule")
public class CreativeAdLearningRuleEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String entityLevel;
    private String entityId;
    private String entityName;
    private String channelCode;
    private Integer timeDays;
    private BigDecimal costThreshold;
    private Instant firstMetricTime;
    private BigDecimal cumulativeCost;
    private Boolean isLearning;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public CreativeAdLearningRuleEntity() {}

    public CreativeAdLearningRuleEntity(String id, String tenantId, String entityLevel, String entityId,
                                        String entityName, String channelCode, Integer timeDays,
                                        BigDecimal costThreshold, Instant firstMetricTime, BigDecimal cumulativeCost,
                                        Boolean isLearning, Boolean enabled, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.entityLevel = entityLevel;
        this.entityId = entityId;
        this.entityName = entityName;
        this.channelCode = channelCode;
        this.timeDays = timeDays;
        this.costThreshold = costThreshold;
        this.firstMetricTime = firstMetricTime;
        this.cumulativeCost = cumulativeCost;
        this.isLearning = isLearning;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEntityLevel() { return entityLevel; }
    public void setEntityLevel(String entityLevel) { this.entityLevel = entityLevel; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }

    public Integer getTimeDays() { return timeDays; }
    public void setTimeDays(Integer timeDays) { this.timeDays = timeDays; }

    public BigDecimal getCostThreshold() { return costThreshold; }
    public void setCostThreshold(BigDecimal costThreshold) { this.costThreshold = costThreshold; }

    public Instant getFirstMetricTime() { return firstMetricTime; }
    public void setFirstMetricTime(Instant firstMetricTime) { this.firstMetricTime = firstMetricTime; }

    public BigDecimal getCumulativeCost() { return cumulativeCost; }
    public void setCumulativeCost(BigDecimal cumulativeCost) { this.cumulativeCost = cumulativeCost; }

    public Boolean getIsLearning() { return isLearning; }
    public void setIsLearning(Boolean isLearning) { this.isLearning = isLearning; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
