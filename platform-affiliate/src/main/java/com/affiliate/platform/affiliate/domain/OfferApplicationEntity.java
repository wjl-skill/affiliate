package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Offer申请实体
 */
@Entity
@Table(name = "affiliate_offer_application", indexes = {
        @Index(name = "idx_app_offer", columnList = "offer_id"),
        @Index(name = "idx_app_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_app_status", columnList = "status"),
        @Index(name = "idx_app_created", columnList = "created_at"),
        @Index(name = "idx_app_unique", columnList = "offer_id,affiliate_id", unique = true)
})
public class OfferApplicationEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "promotion_plan", columnDefinition = "TEXT")
    private String promotionPlan;

    @Column(name = "traffic_sources", columnDefinition = "TEXT")
    private String trafficSources; // JSON array

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // Constructors
    public OfferApplicationEntity() {
    }

    public OfferApplicationEntity(String id, String offerId, String affiliateId,
                                 String promotionPlan, String trafficSources, String status,
                                 String reviewerId, String reviewNote, Instant createdAt,
                                 Instant reviewedAt) {
        this.id = id;
        this.offerId = offerId;
        this.affiliateId = affiliateId;
        this.promotionPlan = promotionPlan;
        this.trafficSources = trafficSources;
        this.status = status;
        this.reviewerId = reviewerId;
        this.reviewNote = reviewNote;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    public String getPromotionPlan() {
        return promotionPlan;
    }

    public void setPromotionPlan(String promotionPlan) {
        this.promotionPlan = promotionPlan;
    }

    public String getTrafficSources() {
        return trafficSources;
    }

    public void setTrafficSources(String trafficSources) {
        this.trafficSources = trafficSources;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
