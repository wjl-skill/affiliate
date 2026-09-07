package com.affiliate.platform.tenant.system;

import java.time.Instant;

/**
 * 推广跟踪分流域名池实体 (Tracking Domain Entity)
 */
public record TrackingDomain(
        String id,
        String tenantId,
        String domain,
        DomainType domainType,
        String cnameTarget,
        DnsStatus dnsStatus,
        SslStatus sslStatus,
        String assignedAffiliateId,
        boolean isDefault,
        Status status,
        Instant createdAt
) {
    public enum DomainType {
        /** 推广点击跳转域名 */
        TRACKING,
        /** 落地页中转跳转域名 */
        LANDING_REDIRECT,
        /** S2S Postback 接口域名 */
        POSTBACK_API
    }

    public enum DnsStatus {
        PENDING_CNAME,
        VERIFIED,
        DNS_FAILED
    }

    public enum SslStatus {
        AUTO_SSL_ACTIVE,
        CUSTOM_CERT,
        EXPIRED
    }

    public enum Status {
        ACTIVE,
        DISABLED,
        FLAGGED_RISK
    }

    public TrackingDomain withDnsStatus(DnsStatus newStatus) {
        return new TrackingDomain(id, tenantId, domain, domainType, cnameTarget, newStatus, sslStatus, assignedAffiliateId, isDefault, status, createdAt);
    }

    public TrackingDomain withDefault(boolean def) {
        return new TrackingDomain(id, tenantId, domain, domainType, cnameTarget, dnsStatus, sslStatus, assignedAffiliateId, def, status, createdAt);
    }
}
