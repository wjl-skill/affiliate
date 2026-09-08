package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Webhook端点实体
 */
@Entity
@Table(name = "affiliate_webhook_endpoint", indexes = {
        @Index(name = "idx_webhook_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_webhook_active", columnList = "active")
})
public class WebhookEndpointEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "url", nullable = false, length = 512)
    private String url;

    @Column(name = "secret", nullable = false, length = 128)
    private String secret; // HMAC signing key

    @Column(name = "subscribed_types", nullable = false, columnDefinition = "TEXT")
    private String subscribedTypes; // JSON array

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public WebhookEndpointEntity() {
    }

    public WebhookEndpointEntity(String id, String affiliateId, String url, String secret,
                                String subscribedTypes, Boolean active, Integer failureCount,
                                Instant lastFailedAt, Instant lastSuccessAt,
                                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.url = url;
        this.secret = secret;
        this.subscribedTypes = subscribedTypes;
        this.active = active;
        this.failureCount = failureCount;
        this.lastFailedAt = lastFailedAt;
        this.lastSuccessAt = lastSuccessAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSubscribedTypes() {
        return subscribedTypes;
    }

    public void setSubscribedTypes(String subscribedTypes) {
        this.subscribedTypes = subscribedTypes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Integer failureCount) {
        this.failureCount = failureCount;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(Instant lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (active == null) {
            active = true;
        }
        if (failureCount == null) {
            failureCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
