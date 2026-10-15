package com.affiliate.platform.service.ai.provider.dto;

import java.math.BigDecimal;

/**
 * 供应商任务轮询查询结果 DTO (Provider Query Result)
 */
public record ProviderQueryResult(
        String remoteTaskId,
        Status status,
        String videoUrl,
        String errorCode,
        String errorMessage,
        BigDecimal usageQuantity,
        String usageUnit
) {
    public enum Status {
        PENDING,
        PROCESSING,
        SUCCEEDED,
        FAILED
    }

    public static ProviderQueryResult processing(String remoteTaskId) {
        return new ProviderQueryResult(remoteTaskId, Status.PROCESSING, null, null, null, BigDecimal.ZERO, "SECOND");
    }

    public static ProviderQueryResult succeeded(String remoteTaskId, String videoUrl, BigDecimal duration) {
        return new ProviderQueryResult(remoteTaskId, Status.SUCCEEDED, videoUrl, null, null, duration, "SECOND");
    }

    public static ProviderQueryResult failed(String remoteTaskId, String errorCode, String errorMessage) {
        return new ProviderQueryResult(remoteTaskId, Status.FAILED, null, errorCode, errorMessage, BigDecimal.ZERO, "SECOND");
    }
}
