package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 服务条款接受记录实体
 */
@Entity
@Table(name = "affiliate_terms_acceptance", indexes = {
        @Index(name = "idx_terms_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_terms_version", columnList = "version"),
        @Index(name = "idx_terms_accepted", columnList = "accepted_at")
})
public class TermsAcceptanceEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    // Constructors
    public TermsAcceptanceEntity() {
    }

    public TermsAcceptanceEntity(String id, String affiliateId, String version,
                                String ipAddress, String userAgent, Instant acceptedAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.version = version;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.acceptedAt = acceptedAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    @PrePersist
    public void prePersist() {
        if (acceptedAt == null) {
            acceptedAt = Instant.now();
        }
    }
}
