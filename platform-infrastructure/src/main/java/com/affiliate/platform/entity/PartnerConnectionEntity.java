package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 外部合作方连接持久化实体 (PartnerConnection MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `partner_connection`。
 */
@TableName("partner_connection")
public class PartnerConnectionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String name;

    private String type;

    private String endpoint;

    private String settings; // JSONB

    private String status;

    private Instant updatedAt;

    public PartnerConnectionEntity() {}

    public PartnerConnectionEntity(String id, String tenantId, String name, String type,
                                   String endpoint, String settings, String status, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.type = type;
        this.endpoint = endpoint;
        this.settings = settings;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
