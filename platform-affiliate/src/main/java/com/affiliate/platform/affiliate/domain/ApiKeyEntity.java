package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * API Key 实体类
 */
@Entity
@Table(name = "affiliate_api_key", indexes = {
        @Index(name = "idx_api_key_secret", columnList = "secret_key", unique = true),
        @Index(name = "idx_api_key_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_api_key_status", columnList = "status"),
        @Index(name = "idx_api_key_expires", columnList = "expires_at")
})
public class ApiKeyEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "secret_key", nullable = false, unique = true, length = 128)
    private String secretKey;

    @Column(name = "scopes", nullable = false, length = 1000)
    private String scopes; // JSON 数组，例如：["READ_OFFERS","WRITE_CONVERSIONS"]

    @Column(name = "environment", nullable = false, length = 20)
    private String environment; // LIVE, TEST

    @Column(name = "status", nullable = false, length = 20)
    private String status; // ACTIVE, DEPRECATED, REVOKED, SUSPENDED

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;

    @Column(name = "usage_count", nullable = false)
    private Long usageCount = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // ========== Constructors ==========

    public ApiKeyEntity() {
    }

    public ApiKeyEntity(String id, String affiliateId, String name, String secretKey,
                        String scopes, String environment, String status, Instant expiresAt,
                        String revokedReason, Long usageCount, Instant createdAt,
                        Instant lastUsedAt, Instant revokedAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.name = name;
        this.secretKey = secretKey;
        this.scopes = scopes;
        this.environment = environment;
        this.status = status;
        this.expiresAt = expiresAt;
        this.revokedReason = revokedReason;
        this.usageCount = usageCount;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
    }

    // ========== Getters and Setters ==========

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }

    public Long getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Long usageCount) {
        this.usageCount = usageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    // ========== PrePersist ==========

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (usageCount == null) {
            usageCount = 0L;
        }
    }
}
