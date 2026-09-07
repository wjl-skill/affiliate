package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 广告活动持久化实体 (Campaign MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `campaign`。
 */
@TableName("campaign")
public class CampaignEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String advertiserId;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal dailyBudget;

    private BigDecimal maxBid;

    private String targetDomains; // JSONB

    private String targetDeviceTypes; // JSONB

    private String status;

    private Instant createdAt;

    public CampaignEntity() {}

    public CampaignEntity(String id, String tenantId, String advertiserId, String name,
                          LocalDate startDate, LocalDate endDate, BigDecimal dailyBudget, BigDecimal maxBid,
                          String targetDomains, String targetDeviceTypes, String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.advertiserId = advertiserId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dailyBudget = dailyBudget;
        this.maxBid = maxBid;
        this.targetDomains = targetDomains;
        this.targetDeviceTypes = targetDeviceTypes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAdvertiserId() { return advertiserId; }
    public void setAdvertiserId(String advertiserId) { this.advertiserId = advertiserId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getDailyBudget() { return dailyBudget; }
    public void setDailyBudget(BigDecimal dailyBudget) { this.dailyBudget = dailyBudget; }

    public BigDecimal getMaxBid() { return maxBid; }
    public void setMaxBid(BigDecimal maxBid) { this.maxBid = maxBid; }

    public String getTargetDomains() { return targetDomains; }
    public void setTargetDomains(String targetDomains) { this.targetDomains = targetDomains; }

    public String getTargetDeviceTypes() { return targetDeviceTypes; }
    public void setTargetDeviceTypes(String targetDeviceTypes) { this.targetDeviceTypes = targetDeviceTypes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
