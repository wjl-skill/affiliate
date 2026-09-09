package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 通知偏好设置实体
 */
@Entity
@Table(name = "affiliate_notification_preference", indexes = {
        @Index(name = "idx_pref_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_pref_unique", columnList = "affiliate_id,notification_type", unique = true)
})
@com.baomidou.mybatisplus.annotation.TableName("affiliate_notification_preference")
public class NotificationPreferenceEntity {

    @Id
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "notification_type", nullable = false, length = 30)
    private String notificationType; // CONVERSION, PAYMENT, OFFER_UPDATE, SYSTEM, FRAUD_ALERT, ACCOUNT, MARKETING

    @Column(name = "enable_email", nullable = false)
    private Boolean enableEmail;

    @Column(name = "enable_sms", nullable = false)
    private Boolean enableSms;

    @Column(name = "enable_webhook", nullable = false)
    private Boolean enableWebhook;

    @Column(name = "enable_in_app", nullable = false)
    private Boolean enableInApp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Constructors
    public NotificationPreferenceEntity() {
    }

    public NotificationPreferenceEntity(String id, String affiliateId, String notificationType,
                                       Boolean enableEmail, Boolean enableSms, Boolean enableWebhook,
                                       Boolean enableInApp, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.notificationType = notificationType;
        this.enableEmail = enableEmail;
        this.enableSms = enableSms;
        this.enableWebhook = enableWebhook;
        this.enableInApp = enableInApp;
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

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public Boolean getEnableEmail() {
        return enableEmail;
    }

    public void setEnableEmail(Boolean enableEmail) {
        this.enableEmail = enableEmail;
    }

    public Boolean getEnableSms() {
        return enableSms;
    }

    public void setEnableSms(Boolean enableSms) {
        this.enableSms = enableSms;
    }

    public Boolean getEnableWebhook() {
        return enableWebhook;
    }

    public void setEnableWebhook(Boolean enableWebhook) {
        this.enableWebhook = enableWebhook;
    }

    public Boolean getEnableInApp() {
        return enableInApp;
    }

    public void setEnableInApp(Boolean enableInApp) {
        this.enableInApp = enableInApp;
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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
