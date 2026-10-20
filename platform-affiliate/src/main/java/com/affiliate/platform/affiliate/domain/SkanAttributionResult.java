package com.affiliate.platform.affiliate.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Apple SKAdNetwork 4.0 归因解析结果记录
 */
public record SkanAttributionResult(
        String id,
        String tenantId,
        String transactionId,
        String adNetworkId,
        String sourceIdentifier,
        String campaignId,
        String placementHint,
        String creativeHint,
        int postbackSequenceIndex,
        Integer conversionValueFine,
        String conversionValueCoarse,
        BigDecimal estimatedRevenue,
        int fidelityType,
        String skanVersion,
        Instant receivedAt,
        String status
) {
    public SkanAttributionResult {
        tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        estimatedRevenue = estimatedRevenue != null ? estimatedRevenue : BigDecimal.ZERO;
        receivedAt = receivedAt != null ? receivedAt : Instant.now();
        status = (status != null && !status.isBlank()) ? status : "PROCESSED";
    }
}
