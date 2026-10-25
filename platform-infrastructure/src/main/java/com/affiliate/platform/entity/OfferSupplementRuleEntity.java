package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * Offer 流量补量规则配置持久化实体 (Offer Supplement Rule Entity)
 * <p>
 * 映射数据库表 `affiliate_offer_supplement_rule` (V20)。
 */
@TableName("affiliate_offer_supplement_rule")
public class OfferSupplementRuleEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String offerId;
    private String affiliateId;
    private String mmpPlatform;
    private String trackingUrlTemplate;
    private String storageConfigId;
    private String objectKeyOrPath;
    private String osFilter;
    private Integer batchSize;
    private Integer qpsLimit;
    private Boolean autoEnabled;
    private Integer cronIntervalMinutes;
    private Integer lastCursorOffset;
    private Long totalDispatchedClicks;
    private String status;
    private Instant lastExecutedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public OfferSupplementRuleEntity() {
    }

    public OfferSupplementRuleEntity(
            String id,
            String tenantId,
            String offerId,
            String affiliateId,
            String mmpPlatform,
            String trackingUrlTemplate,
            String storageConfigId,
            String objectKeyOrPath,
            String osFilter,
            Integer batchSize,
            Integer qpsLimit,
            Boolean autoEnabled,
            Integer cronIntervalMinutes,
            Integer lastCursorOffset,
            Long totalDispatchedClicks,
            String status,
            Instant lastExecutedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.offerId = offerId;
        this.affiliateId = affiliateId;
        this.mmpPlatform = mmpPlatform;
        this.trackingUrlTemplate = trackingUrlTemplate;
        this.storageConfigId = storageConfigId;
        this.objectKeyOrPath = objectKeyOrPath;
        this.osFilter = osFilter;
        this.batchSize = batchSize;
        this.qpsLimit = qpsLimit;
        this.autoEnabled = autoEnabled;
        this.cronIntervalMinutes = cronIntervalMinutes;
        this.lastCursorOffset = lastCursorOffset;
        this.totalDispatchedClicks = totalDispatchedClicks;
        this.status = status;
        this.lastExecutedAt = lastExecutedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getMmpPlatform() { return mmpPlatform; }
    public void setMmpPlatform(String mmpPlatform) { this.mmpPlatform = mmpPlatform; }

    public String getTrackingUrlTemplate() { return trackingUrlTemplate; }
    public void setTrackingUrlTemplate(String trackingUrlTemplate) { this.trackingUrlTemplate = trackingUrlTemplate; }

    public String getStorageConfigId() { return storageConfigId; }
    public void setStorageConfigId(String storageConfigId) { this.storageConfigId = storageConfigId; }

    public String getObjectKeyOrPath() { return objectKeyOrPath; }
    public void setObjectKeyOrPath(String objectKeyOrPath) { this.objectKeyOrPath = objectKeyOrPath; }

    public String getOsFilter() { return osFilter; }
    public void setOsFilter(String osFilter) { this.osFilter = osFilter; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getQpsLimit() { return qpsLimit; }
    public void setQpsLimit(Integer qpsLimit) { this.qpsLimit = qpsLimit; }

    public Boolean getAutoEnabled() { return autoEnabled; }
    public void setAutoEnabled(Boolean autoEnabled) { this.autoEnabled = autoEnabled; }

    public Integer getCronIntervalMinutes() { return cronIntervalMinutes; }
    public void setCronIntervalMinutes(Integer cronIntervalMinutes) { this.cronIntervalMinutes = cronIntervalMinutes; }

    public Integer getLastCursorOffset() { return lastCursorOffset; }
    public void setLastCursorOffset(Integer lastCursorOffset) { this.lastCursorOffset = lastCursorOffset; }

    public Long getTotalDispatchedClicks() { return totalDispatchedClicks; }
    public void setTotalDispatchedClicks(Long totalDispatchedClicks) { this.totalDispatchedClicks = totalDispatchedClicks; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastExecutedAt() { return lastExecutedAt; }
    public void setLastExecutedAt(Instant lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
