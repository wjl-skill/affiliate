package com.affiliate.platform.affiliate.domain;

import java.time.Instant;

/**
 * Offer 补量点击与归因平台 (AppsFlyer / Adjust) HTTP 推送审计记录
 */
public record OfferSupplementLog(
        String id,
        String tenantId,
        String taskBatchId,
        String ruleId,
        String offerId,
        String affiliateId,
        String clickId,
        String mmpPlatform,
        String profileId,
        String idfa,
        String gadvid,
        String ip,
        String userAgent,
        String os,
        String dsPublisherId,
        String dsAdx,
        String sourceTag,
        Integer modelFlag,
        String targetUrl,
        int httpStatus,
        boolean success,
        String responseSummary,
        long latencyMs,
        Instant createdAt
) {
}
