package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 推广跟踪域名池实体 (Tracking Domain Entity)
 * <p>
 * 映射数据库表 `sys_tracking_domain`。
 */
@TableName("sys_tracking_domain")
public class TrackingDomainEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String domain;
    private String domainType;
    private String cnameTarget;
    private String dnsStatus;
    private String sslStatus;
    private String assignedAffiliateId;
    private Boolean isDefault;
    private String status;
    private Instant createdAt;

    public TrackingDomainEntity() {}

    public TrackingDomainEntity(String id, String tenantId, String domain, String domainType,
                                String cnameTarget, String dnsStatus, String sslStatus,
                                String assignedAffiliateId, Boolean isDefault, String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.domain = domain;
        this.domainType = domainType;
        this.cnameTarget = cnameTarget;
        this.dnsStatus = dnsStatus;
        this.sslStatus = sslStatus;
        this.assignedAffiliateId = assignedAffiliateId;
        this.isDefault = isDefault;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDomainType() { return domainType; }
    public void setDomainType(String domainType) { this.domainType = domainType; }

    public String getCnameTarget() { return cnameTarget; }
    public void setCnameTarget(String cnameTarget) { this.cnameTarget = cnameTarget; }

    public String getDnsStatus() { return dnsStatus; }
    public void setDnsStatus(String dnsStatus) { this.dnsStatus = dnsStatus; }

    public String getSslStatus() { return sslStatus; }
    public void setSslStatus(String sslStatus) { this.sslStatus = sslStatus; }

    public String getAssignedAffiliateId() { return assignedAffiliateId; }
    public void setAssignedAffiliateId(String assignedAffiliateId) { this.assignedAffiliateId = assignedAffiliateId; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean aDefault) { isDefault = aDefault; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
