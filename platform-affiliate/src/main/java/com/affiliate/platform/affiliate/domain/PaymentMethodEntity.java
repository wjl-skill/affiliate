package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 支付方式实体
 */
@Entity
@Table(name = "affiliate_payment_method", indexes = {
        @Index(name = "idx_payment_method_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_payment_method_status", columnList = "status"),
        @Index(name = "idx_payment_method_primary", columnList = "affiliate_id,is_primary")
})
public class PaymentMethodEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "type", nullable = false, length = 30)
    private String type; // PAYPAL, STRIPE, BANK_TRANSFER, WIRE_TRANSFER, CHECK, CRYPTOCURRENCY

    @Column(name = "credentials", nullable = false, columnDefinition = "TEXT")
    private String credentials; // JSON: {"email":"...", "accountNumber":"...", etc}

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // PENDING_VERIFICATION, VERIFIED, SUSPENDED, REMOVED

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    // Constructors
    public PaymentMethodEntity() {
    }

    public PaymentMethodEntity(String id, String affiliateId, String type, String credentials,
                              String currency, Boolean isPrimary, String status,
                              Instant lastUsedAt, Instant createdAt, Instant verifiedAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.type = type;
        this.credentials = credentials;
        this.currency = currency;
        this.isPrimary = isPrimary;
        this.status = status;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
        this.verifiedAt = verifiedAt;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
