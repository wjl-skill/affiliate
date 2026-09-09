package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** CDP 一方客户档案持久化实体。JSONB 字段以文本承载，序列化由领域服务负责。 */
@TableName("cdp_profile")
public class CdpProfileEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String tenantId;
    private String primaryId;
    private String status;
    private String identifiers;
    private String attributes;
    private String traits;
    private Instant lastSeenAt;
    private Instant createdAt;
    public CdpProfileEntity() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPrimaryId() { return primaryId; }
    public void setPrimaryId(String primaryId) { this.primaryId = primaryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdentifiers() { return identifiers; }
    public void setIdentifiers(String identifiers) { this.identifiers = identifiers; }
    public String getAttributes() { return attributes; }
    public void setAttributes(String attributes) { this.attributes = attributes; }
    public String getTraits() { return traits; }
    public void setTraits(String traits) { this.traits = traits; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
