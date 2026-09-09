package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 实时竞价预算预占流水实体。 */
@TableName("budget_reservation")
public class BudgetReservationEntity {
    @TableId(type = IdType.INPUT) private UUID id;
    private String tenantId;
    private String campaignId;
    private String userId;
    private BigDecimal amount;
    private String status;
    private Instant createdAt;
    private Instant confirmedAt;
    private Instant expiresAt;
    public BudgetReservationEntity() {}
    public BudgetReservationEntity(UUID id,String tenantId,String campaignId,String userId,BigDecimal amount,String status,Instant createdAt,Instant confirmedAt,Instant expiresAt){this.id=id;this.tenantId=tenantId;this.campaignId=campaignId;this.userId=userId;this.amount=amount;this.status=status;this.createdAt=createdAt;this.confirmedAt=confirmedAt;this.expiresAt=expiresAt;}
    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getCampaignId(){return campaignId;} public void setCampaignId(String v){campaignId=v;}
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(Instant v){confirmedAt=v;}
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
}
