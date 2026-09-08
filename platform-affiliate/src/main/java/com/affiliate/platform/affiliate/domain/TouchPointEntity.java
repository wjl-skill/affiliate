package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 用户触点实体（多触点归因）
 */
@Entity
@Table(name = "affiliate_touch_point", indexes = {
        @Index(name = "idx_touchpoint_user", columnList = "user_id,timestamp"),
        @Index(name = "idx_touchpoint_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_touchpoint_click", columnList = "click_id")
})
public class TouchPointEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "type", nullable = false, length = 20)
    private String type; // IMPRESSION, CLICK, VIEW, ENGAGEMENT

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "click_id", length = 128)
    private String clickId;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "medium", length = 100)
    private String medium;

    @Column(name = "campaign", length = 255)
    private String campaign;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    // Constructors
    public TouchPointEntity() {
    }

    public TouchPointEntity(String id, String userId, String sessionId, String type,
                           String affiliateId, String offerId, String clickId,
                           String source, String medium, String campaign, Instant timestamp) {
        this.id = id;
        this.userId = userId;
        this.sessionId = sessionId;
        this.type = type;
        this.affiliateId = affiliateId;
        this.offerId = offerId;
        this.clickId = clickId;
        this.source = source;
        this.medium = medium;
        this.campaign = campaign;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getClickId() {
        return clickId;
    }

    public void setClickId(String clickId) {
        this.clickId = clickId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public String getCampaign() {
        return campaign;
    }

    public void setCampaign(String campaign) {
        this.campaign = campaign;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
