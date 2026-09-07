package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 4D 反作弊风控黑名单持久化实体 (Anti-Fraud Blacklist MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_antifraud_blacklist`。
 */
@TableName("affiliate_antifraud_blacklist")
public class AntiFraudBlacklistEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String targetType; // 'IP', 'SUB_ID', 'AFFILIATE_ID'
    private String targetValue;
    private String reason;
    private String operator;
    private String status;     // 'ACTIVE', 'REVOKED'
    private Instant expiresAt;
    private Instant createdAt;

    public AntiFraudBlacklistEntity() {}

    public AntiFraudBlacklistEntity(String id, String tenantId, String targetType, String targetValue,
                                    String reason, String operator, String status, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.targetType = targetType;
        this.targetValue = targetValue;
        this.reason = reason;
        this.operator = operator;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
