package com.affiliate.platform.service.ai.provider.dto;

import java.time.Instant;

/**
 * 供应商任务提交响应 DTO (Provider Submit Result)
 */
public record ProviderSubmitResult(
        String remoteTaskId,
        String status, // "PENDING", "PROCESSING"
        Instant submittedAt
) {
    public static ProviderSubmitResult of(String remoteTaskId) {
        return new ProviderSubmitResult(remoteTaskId, "PROCESSING", Instant.now());
    }
}
