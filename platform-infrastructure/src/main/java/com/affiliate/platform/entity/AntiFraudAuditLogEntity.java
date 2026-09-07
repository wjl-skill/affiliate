package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 4D 反作弊审计流水持久化实体 (Anti-Fraud Audit Log MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_antifraud_audit_log`。
 */
@TableName("affiliate_antifraud_audit_log")
public class AntiFraudAuditLogEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String transactionId;
    private String clickId;
    private String affiliateId;
    private String ip;
    private BigDecimal ctitSeconds;
    private Integer riskScore;
    private String primaryReason;
    private String action; // 'REJECTED', 'FLAGGED', 'APPROVED'
    private String detailsJson;
    private Instant createdAt;

    public AntiFraudAuditLogEntity() {}

    public AntiFraudAuditLogEntity(String id, String tenantId, String transactionId, String clickId,
                                   String affiliateId, String ip, BigDecimal ctitSeconds, Integer riskScore,
                                   String primaryReason, String action, String detailsJson, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.transactionId = transactionId;
        this.clickId = clickId;
        this.affiliateId = affiliateId;
        this.ip = ip;
        this.ctitSeconds = ctitSeconds;
        this.riskScore = riskScore;
        this.primaryReason = primaryReason;
        this.action = action;
        this.detailsJson = detailsJson;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getClickId() { return clickId; }
    public void setClickId(String clickId) { this.clickId = clickId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public BigDecimal getCtitSeconds() { return ctitSeconds; }
    public void setCtitSeconds(BigDecimal ctitSeconds) { this.ctitSeconds = ctitSeconds; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getPrimaryReason() { return primaryReason; }
    public void setPrimaryReason(String primaryReason) { this.primaryReason = primaryReason; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
