package com.affiliate.platform.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Offer目标（转化事件）实体
 */
@Entity
@Table(
        name = "affiliate_offer_goal",
        indexes = {
                @Index(name = "idx_offer_goal_offer_id", columnList = "offer_id"),
                @Index(name = "idx_offer_goal_status", columnList = "status"),
                @Index(name = "idx_offer_goal_type", columnList = "goal_type")
        }
)
public class OfferGoalEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "goal_name", nullable = false, length = 128)
    private String goalName;

    @Column(name = "goal_type", nullable = false, length = 32)
    private String goalType;

    @Column(name = "payout_model", nullable = false, length = 32)
    private String payoutModel;

    @Column(name = "payout", precision = 12, scale = 4)
    private BigDecimal payout;

    @Column(name = "revenue", precision = 12, scale = 4)
    private BigDecimal revenue;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OfferGoalEntity() {
    }

    public OfferGoalEntity(
            String id,
            String tenantId,
            String offerId,
            String goalName,
            String goalType,
            String payoutModel,
            BigDecimal payout,
            BigDecimal revenue,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.offerId = offerId;
        this.goalName = goalName;
        this.goalType = goalType;
        this.payoutModel = payoutModel;
        this.payout = payout;
        this.revenue = revenue;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getGoalName() {
        return goalName;
    }

    public void setGoalName(String goalName) {
        this.goalName = goalName;
    }

    public String getGoalType() {
        return goalType;
    }

    public void setGoalType(String goalType) {
        this.goalType = goalType;
    }

    public String getPayoutModel() {
        return payoutModel;
    }

    public void setPayoutModel(String payoutModel) {
        this.payoutModel = payoutModel;
    }

    public BigDecimal getPayout() {
        return payout;
    }

    public void setPayout(BigDecimal payout) {
        this.payout = payout;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
