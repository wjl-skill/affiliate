package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 合规违规记录实体
 */
@Entity
@Table(name = "affiliate_compliance_violation", indexes = {
        @Index(name = "idx_violation_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_violation_type", columnList = "type"),
        @Index(name = "idx_violation_severity", columnList = "severity"),
        @Index(name = "idx_violation_status", columnList = "status"),
        @Index(name = "idx_violation_detected", columnList = "detected_at")
})
@com.baomidou.mybatisplus.annotation.TableName("affiliate_compliance_violation")
public class ComplianceViolationEntity {

    @Id
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "type", nullable = false, length = 50)
    private String type; // FRAUD, TRADEMARK_VIOLATION, COOKIE_STUFFING, etc.

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // OPEN, WARNING_ISSUED, UNDER_INVESTIGATION, RESOLVED, etc.

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // Constructors
    public ComplianceViolationEntity() {
    }

    public ComplianceViolationEntity(String id, String affiliateId, String type,
                                    String description, String severity, String evidence,
                                    String status, String resolution, Instant detectedAt,
                                    Instant resolvedAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.type = type;
        this.description = description;
        this.severity = severity;
        this.evidence = evidence;
        this.status = status;
        this.resolution = resolution;
        this.detectedAt = detectedAt;
        this.resolvedAt = resolvedAt;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    @PrePersist
    public void prePersist() {
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }
}
