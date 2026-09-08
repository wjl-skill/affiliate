package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * KYC身份验证实体
 */
@Entity
@Table(name = "affiliate_kyc_verification", indexes = {
        @Index(name = "idx_kyc_affiliate", columnList = "affiliate_id", unique = true),
        @Index(name = "idx_kyc_status", columnList = "status")
})
public class KycVerificationEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, unique = true, length = 64)
    private String affiliateId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "date_of_birth", length = 10)
    private String dateOfBirth;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "id_document_url", length = 512)
    private String idDocumentUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, VERIFIED, REJECTED, EXPIRED

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Constructors
    public KycVerificationEntity() {
    }

    public KycVerificationEntity(String id, String affiliateId, String fullName,
                                String dateOfBirth, String address, String idDocumentUrl,
                                String status, String rejectionReason, Integer riskScore,
                                Instant initiatedAt, Instant completedAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.idDocumentUrl = idDocumentUrl;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.riskScore = riskScore;
        this.initiatedAt = initiatedAt;
        this.completedAt = completedAt;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIdDocumentUrl() {
        return idDocumentUrl;
    }

    public void setIdDocumentUrl(String idDocumentUrl) {
        this.idDocumentUrl = idDocumentUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public Instant getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(Instant initiatedAt) {
        this.initiatedAt = initiatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @PrePersist
    public void prePersist() {
        if (initiatedAt == null) {
            initiatedAt = Instant.now();
        }
    }
}
