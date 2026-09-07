package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 租户持久化实体 (Tenant MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `tenant`。
 */
@TableName("tenant")
public class TenantEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String status;

    private Instant createdAt;

    public TenantEntity() {}

    public TenantEntity(String id, String name, String status, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
