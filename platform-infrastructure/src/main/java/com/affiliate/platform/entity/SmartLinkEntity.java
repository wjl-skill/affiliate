package com.affiliate.platform.entity;

import com.affiliate.platform.handler.PostgresStringArrayTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.List;

/**
 * 智能分流链接实体 (SmartLink / TDS MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_smart_link`。
 */
@TableName(value = "affiliate_smart_link", autoResultMap = true)
public class SmartLinkEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String name;
    private String category;

    @TableField(value = "target_offer_ids", typeHandler = PostgresStringArrayTypeHandler.class)
    private List<String> targetOfferIds;

    private String routingStrategy;
    private String fallbackOfferId;
    private Instant createdAt;

    public SmartLinkEntity() {}

    public SmartLinkEntity(String id, String tenantId, String name, String category,
                           List<String> targetOfferIds, String routingStrategy,
                           String fallbackOfferId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.category = category;
        this.targetOfferIds = targetOfferIds;
        this.routingStrategy = routingStrategy;
        this.fallbackOfferId = fallbackOfferId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTargetOfferIds() { return targetOfferIds; }
    public void setTargetOfferIds(List<String> targetOfferIds) { this.targetOfferIds = targetOfferIds; }

    public String getRoutingStrategy() { return routingStrategy; }
    public void setRoutingStrategy(String routingStrategy) { this.routingStrategy = routingStrategy; }

    public String getFallbackOfferId() { return fallbackOfferId; }
    public void setFallbackOfferId(String fallbackOfferId) { this.fallbackOfferId = fallbackOfferId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
