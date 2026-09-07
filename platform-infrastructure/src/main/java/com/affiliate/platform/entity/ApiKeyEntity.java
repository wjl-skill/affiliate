package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 开放平台 API-Key 凭证持久化实体 (API Key Entity)
 * <p>
 * 映射数据库表 `api_key`。
 */
@TableName("api_key")
public class ApiKeyEntity {

    @TableId(type = IdType.INPUT)
    private String keyId;
    private String tenantId;
    private String secret;
    private Boolean active;
    private Instant createdAt;

    public ApiKeyEntity() {}

    public ApiKeyEntity(String keyId, String tenantId, String secret, Boolean active, Instant createdAt) {
        this.keyId = keyId;
        this.tenantId = tenantId;
        this.secret = secret;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
