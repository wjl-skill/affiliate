package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 网盟多事件转化目标持久化实体 (Offer Goal MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `affiliate_offer_goal`。
 */
@TableName("affiliate_offer_goal")
public class OfferGoalEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String offerId;
    private String goalName;
    private String goalType;
    private String payoutType;
    private BigDecimal payout;
    private BigDecimal revenue;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public OfferGoalEntity() {}

    public OfferGoalEntity(String id, String tenantId, String offerId, String goalName,
                           String goalType, String payoutType, BigDecimal payout,
                           BigDecimal revenue, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.offerId = offerId;
        this.goalName = goalName;
        this.goalType = goalType;
        this.payoutType = payoutType;
        this.payout = payout;
        this.revenue = revenue;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getPayoutType() { return payoutType; }
    public void setPayoutType(String payoutType) { this.payoutType = payoutType; }

    public BigDecimal getPayout() { return payout; }
    public void setPayout(BigDecimal payout) { this.payout = payout; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
