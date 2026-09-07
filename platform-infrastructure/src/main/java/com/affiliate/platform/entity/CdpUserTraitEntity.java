package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CDP 用户行为画像快照持久化实体 (CDP User Trait State MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `cdp_user_trait_state`。
 */
@TableName("cdp_user_trait_state")
public class CdpUserTraitEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String primaryId;
    private BigDecimal totalSpend;
    private Integer purchaseCount;
    private Integer clickCount;
    private Integer pageviewCount;
    private Integer events7dCount;
    private String preferredCategory;
    private String preferredDevice;
    private String traitsJson;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant updatedAt;

    public CdpUserTraitEntity() {}

    public CdpUserTraitEntity(String id, String tenantId, String primaryId, BigDecimal totalSpend,
                              Integer purchaseCount, Integer clickCount, Integer pageviewCount,
                              Integer events7dCount, String preferredCategory, String preferredDevice,
                              String traitsJson, Instant firstSeenAt, Instant lastSeenAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.primaryId = primaryId;
        this.totalSpend = totalSpend;
        this.purchaseCount = purchaseCount;
        this.clickCount = clickCount;
        this.pageviewCount = pageviewCount;
        this.events7dCount = events7dCount;
        this.preferredCategory = preferredCategory;
        this.preferredDevice = preferredDevice;
        this.traitsJson = traitsJson;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPrimaryId() { return primaryId; }
    public void setPrimaryId(String primaryId) { this.primaryId = primaryId; }

    public BigDecimal getTotalSpend() { return totalSpend; }
    public void setTotalSpend(BigDecimal totalSpend) { this.totalSpend = totalSpend; }

    public Integer getPurchaseCount() { return purchaseCount; }
    public void setPurchaseCount(Integer purchaseCount) { this.purchaseCount = purchaseCount; }

    public Integer getClickCount() { return clickCount; }
    public void setClickCount(Integer clickCount) { this.clickCount = clickCount; }

    public Integer getPageviewCount() { return pageviewCount; }
    public void setPageviewCount(Integer pageviewCount) { this.pageviewCount = pageviewCount; }

    public Integer getEvents7dCount() { return events7dCount; }
    public void setEvents7dCount(Integer events7dCount) { this.events7dCount = events7dCount; }

    public String getPreferredCategory() { return preferredCategory; }
    public void setPreferredCategory(String preferredCategory) { this.preferredCategory = preferredCategory; }

    public String getPreferredDevice() { return preferredDevice; }
    public void setPreferredDevice(String preferredDevice) { this.preferredDevice = preferredDevice; }

    public String getTraitsJson() { return traitsJson; }
    public void setTraitsJson(String traitsJson) { this.traitsJson = traitsJson; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
