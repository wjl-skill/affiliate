package com.affiliate.platform.affiliate.domain;

import java.time.Instant;

/**
 * Offer 流量补量规则配置领域记录 (Offer Traffic Supplement Rule)
 */
public record OfferSupplementRule(
        String id,
        String tenantId,
        String offerId,
        String affiliateId,
        String mmpPlatform,
        String trackingUrlTemplate,
        String storageConfigId,
        String objectKeyOrPath,
        String osFilter,
        int batchSize,
        int qpsLimit,
        boolean autoEnabled,
        int cronIntervalMinutes,
        int lastCursorOffset,
        long totalDispatchedClicks,
        String status,
        Instant lastExecutedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public OfferSupplementRule {
        tenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
        affiliateId = (affiliateId == null || affiliateId.isBlank()) ? "aff_supplement" : affiliateId;
        mmpPlatform = (mmpPlatform == null || mmpPlatform.isBlank()) ? "APPSFLYER" : mmpPlatform.toUpperCase();
        osFilter = (osFilter == null || osFilter.isBlank()) ? "ALL" : osFilter;
        batchSize = batchSize <= 0 ? 50 : Math.min(batchSize, 5000);
        qpsLimit = qpsLimit <= 0 ? 20 : Math.min(qpsLimit, 1000);
        cronIntervalMinutes = cronIntervalMinutes <= 0 ? 30 : cronIntervalMinutes;
        lastCursorOffset = Math.max(0, lastCursorOffset);
        totalDispatchedClicks = Math.max(0L, totalDispatchedClicks);
        status = (status == null || status.isBlank()) ? "ACTIVE" : status.toUpperCase();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public OfferSupplementRule withProgress(int nextOffset, long additionalClicks, Instant executedAt) {
        return new OfferSupplementRule(
                id,
                tenantId,
                offerId,
                affiliateId,
                mmpPlatform,
                trackingUrlTemplate,
                storageConfigId,
                objectKeyOrPath,
                osFilter,
                batchSize,
                qpsLimit,
                autoEnabled,
                cronIntervalMinutes,
                nextOffset,
                totalDispatchedClicks + additionalClicks,
                status,
                executedAt,
                createdAt,
                executedAt
        );
    }
}
