package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

/** 广告活动预算账户实体。 */
@TableName("campaign_budget")
public class CampaignBudgetEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String tenantId;
    private String campaignId;
    private BigDecimal totalBudget;
    private BigDecimal dailyBudget;
    private BigDecimal balance;
    private Instant updatedAt;
    public CampaignBudgetEntity() {}
    public CampaignBudgetEntity(String id, String tenantId, String campaignId, BigDecimal totalBudget, BigDecimal dailyBudget, BigDecimal balance, Instant updatedAt) {
        this.id=id; this.tenantId=tenantId; this.campaignId=campaignId; this.totalBudget=totalBudget; this.dailyBudget=dailyBudget; this.balance=balance; this.updatedAt=updatedAt;
    }
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getCampaignId(){return campaignId;} public void setCampaignId(String v){campaignId=v;}
    public BigDecimal getTotalBudget(){return totalBudget;} public void setTotalBudget(BigDecimal v){totalBudget=v;}
    public BigDecimal getDailyBudget(){return dailyBudget;} public void setDailyBudget(BigDecimal v){dailyBudget=v;}
    public BigDecimal getBalance(){return balance;} public void setBalance(BigDecimal v){balance=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
