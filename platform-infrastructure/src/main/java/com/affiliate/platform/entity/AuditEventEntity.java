package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

/**
 * 平台通用安全审计事件持久化实体 (Audit Event Entity)
 * <p>
 * 映射数据库表 `audit_event` (V1)。
 */
@TableName("audit_event")
public class AuditEventEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String tenantId;
    private String actorId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String payload;
    private Instant createdAt;

    public AuditEventEntity() {
    }

    public AuditEventEntity(
            UUID id,
            String tenantId,
            String actorId,
            String action,
            String resourceType,
            String resourceId,
            String payload,
            Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
