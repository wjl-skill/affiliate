package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 税务文档实体
 */
@Entity
@Table(name = "affiliate_tax_document", indexes = {
        @Index(name = "idx_tax_doc_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_tax_doc_status", columnList = "status"),
        @Index(name = "idx_tax_doc_expires", columnList = "expires_at")
})
public class TaxDocumentEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "affiliate_id", nullable = false, length = 64)
    private String affiliateId;

    @Column(name = "form_type", nullable = false, length = 30)
    private String formType; // W9, W8BEN, W8BEN_E, FORM_1099_MISC

    @Column(name = "document_url", nullable = false, length = 512)
    private String documentUrl;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // PENDING_REVIEW, APPROVED, REJECTED, EXPIRED

    @Column(name = "reviewer_note", columnDefinition = "TEXT")
    private String reviewerNote;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // Constructors
    public TaxDocumentEntity() {
    }

    public TaxDocumentEntity(String id, String affiliateId, String formType, String documentUrl,
                            String taxId, String legalName, String country, String status,
                            String reviewerNote, Instant uploadedAt, Instant reviewedAt,
                            Instant expiresAt) {
        this.id = id;
        this.affiliateId = affiliateId;
        this.formType = formType;
        this.documentUrl = documentUrl;
        this.taxId = taxId;
        this.legalName = legalName;
        this.country = country;
        this.status = status;
        this.reviewerNote = reviewerNote;
        this.uploadedAt = uploadedAt;
        this.reviewedAt = reviewedAt;
        this.expiresAt = expiresAt;
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

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
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

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @PrePersist
    public void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }
}
