package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 营销素材实体
 */
@Entity
@Table(name = "affiliate_creative", indexes = {
        @Index(name = "idx_creative_offer", columnList = "offer_id"),
        @Index(name = "idx_creative_type", columnList = "type"),
        @Index(name = "idx_creative_status", columnList = "status"),
        @Index(name = "idx_creative_created", columnList = "created_at")
})
@com.baomidou.mybatisplus.annotation.TableName("affiliate_creative")
public class CreativeEntity {

    @Id
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "type", nullable = false, length = 30)
    private String type; // BANNER, TEXT_LINK, EMAIL_TEMPLATE, SOCIAL_MEDIA, VIDEO, NATIVE, PRODUCT_FEED, COUPON

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "assets", nullable = false, columnDefinition = "TEXT")
    private String assets; // JSON: {"imageUrl":"...", "landingPageUrl":"...", etc}

    @Column(name = "languages", columnDefinition = "TEXT")
    private String languages; // JSON array: ["en", "zh", "es"]

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON: extra metadata

    @Column(name = "status", nullable = false, length = 30)
    private String status; // PENDING_REVIEW, APPROVED, REJECTED, PAUSED, ARCHIVED

    @Column(name = "reviewer_note", columnDefinition = "TEXT")
    private String reviewerNote;

    @Column(name = "clicks", nullable = false)
    private Long clicks;

    @Column(name = "conversions", nullable = false)
    private Long conversions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // Constructors
    public CreativeEntity() {
    }

    public CreativeEntity(String id, String offerId, String type, String name,
                         String description, String assets, String languages,
                         String metadata, String status, String reviewerNote,
                         Long clicks, Long conversions, Instant createdAt,
                         Instant reviewedAt) {
        this.id = id;
        this.offerId = offerId;
        this.type = type;
        this.name = name;
        this.description = description;
        this.assets = assets;
        this.languages = languages;
        this.metadata = metadata;
        this.status = status;
        this.reviewerNote = reviewerNote;
        this.clicks = clicks;
        this.conversions = conversions;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssets() {
        return assets;
    }

    public void setAssets(String assets) {
        this.assets = assets;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public void setReviewerNote(String reviewerNote) {
        this.reviewerNote = reviewerNote;
    }

    public Long getClicks() {
        return clicks;
    }

    public void setClicks(Long clicks) {
        this.clicks = clicks;
    }

    public Long getConversions() {
        return conversions;
    }

    public void setConversions(Long conversions) {
        this.conversions = conversions;
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
        if (clicks == null) {
            clicks = 0L;
        }
        if (conversions == null) {
            conversions = 0L;
        }
    }
}
