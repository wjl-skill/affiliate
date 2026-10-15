package com.affiliate.platform.domain.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 统一 AI 异步生成任务实体记录 (AI Generate Task Record)
 */
public record AiGenerateTaskRecord(
        String id,
        String tenantId,
        String taskNo,
        AiGenerateType generateType,
        String modelCode,
        String providerCode,
        String remoteTaskId,
        AiTaskStatus status,
        String prompt,
        String startImageUrl,
        String endImageUrl,
        List<String> referenceImageUrls,
        String sourceVideoUrl,
        String rawOutputUrl,
        String ossUrl,
        int durationSeconds,
        String usageUnit,
        BigDecimal usageQuantity,
        BigDecimal estimatedCost,
        String costStatus,
        String errorCode,
        String errorMessage,
        String createdBy,
        Instant createdAt,
        Instant finishedAt
) {
    public AiGenerateTaskRecord {
        referenceImageUrls = referenceImageUrls == null ? List.of() : List.copyOf(referenceImageUrls);
        usageQuantity = usageQuantity != null ? usageQuantity : BigDecimal.ZERO;
        estimatedCost = estimatedCost != null ? estimatedCost : BigDecimal.ZERO;
        costStatus = costStatus != null ? costStatus : "PENDING";
        usageUnit = usageUnit != null ? usageUnit : "SECOND";
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static AiGenerateTaskRecord createInitial(
            String id, String tenantId, String taskNo, VideoGenerateRequest req, String providerCode, String createdBy) {
        return new AiGenerateTaskRecord(
                id,
                tenantId != null ? tenantId : "public",
                taskNo,
                req.generateType(),
                req.modelCode(),
                providerCode,
                null,
                AiTaskStatus.PENDING,
                req.prompt(),
                req.startImageUrl(),
                req.endImageUrl(),
                req.referenceImageUrls(),
                req.sourceVideoUrl(),
                null,
                null,
                req.durationSeconds(),
                "SECOND",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PENDING",
                null,
                null,
                createdBy,
                Instant.now(),
                null
        );
    }

    public AiGenerateTaskRecord withProcessing(String remoteTaskId) {
        return new AiGenerateTaskRecord(
                id, tenantId, taskNo, generateType, modelCode, providerCode, remoteTaskId,
                AiTaskStatus.PROCESSING, prompt, startImageUrl, endImageUrl, referenceImageUrls,
                sourceVideoUrl, rawOutputUrl, ossUrl, durationSeconds, usageUnit, usageQuantity,
                estimatedCost, costStatus, null, null, createdBy, createdAt, null
        );
    }

    public AiGenerateTaskRecord withSuccessFetched(String rawOutputUrl) {
        return new AiGenerateTaskRecord(
                id, tenantId, taskNo, generateType, modelCode, providerCode, remoteTaskId,
                AiTaskStatus.SUCCESS_FETCHED, prompt, startImageUrl, endImageUrl, referenceImageUrls,
                sourceVideoUrl, rawOutputUrl, ossUrl, durationSeconds, usageUnit, usageQuantity,
                estimatedCost, costStatus, null, null, createdBy, createdAt, null
        );
    }

    public AiGenerateTaskRecord withSuccess(String ossUrl, BigDecimal usageQuantity, BigDecimal estimatedCost) {
        return new AiGenerateTaskRecord(
                id, tenantId, taskNo, generateType, modelCode, providerCode, remoteTaskId,
                AiTaskStatus.SUCCESS, prompt, startImageUrl, endImageUrl, referenceImageUrls,
                sourceVideoUrl, rawOutputUrl, ossUrl, durationSeconds, usageUnit, usageQuantity,
                estimatedCost, "CALCULATED", null, null, createdBy, createdAt, Instant.now()
        );
    }

    public AiGenerateTaskRecord withFailed(String errorCode, String errorMessage) {
        return new AiGenerateTaskRecord(
                id, tenantId, taskNo, generateType, modelCode, providerCode, remoteTaskId,
                AiTaskStatus.FAILED, prompt, startImageUrl, endImageUrl, referenceImageUrls,
                sourceVideoUrl, rawOutputUrl, ossUrl, durationSeconds, usageUnit, usageQuantity,
                estimatedCost, "CANCELLED", errorCode, errorMessage, createdBy, createdAt, Instant.now()
        );
    }

    public AiGenerateTaskRecord withCancelled() {
        return new AiGenerateTaskRecord(
                id, tenantId, taskNo, generateType, modelCode, providerCode, remoteTaskId,
                AiTaskStatus.CANCELLED, prompt, startImageUrl, endImageUrl, referenceImageUrls,
                sourceVideoUrl, rawOutputUrl, ossUrl, durationSeconds, usageUnit, usageQuantity,
                estimatedCost, "CANCELLED", "TASK_CANCELLED", "User cancelled task", createdBy, createdAt, Instant.now()
        );
    }

    public AiGenerateTaskRecord withDiscarded(String reason) {
        return new AiGenerateTaskRecord(
                id, tenantId, taskNo, generateType, modelCode, providerCode, remoteTaskId,
                AiTaskStatus.DISCARDED, prompt, startImageUrl, endImageUrl, referenceImageUrls,
                sourceVideoUrl, rawOutputUrl, ossUrl, durationSeconds, usageUnit, usageQuantity,
                estimatedCost, "DISCARDED", "TASK_DISCARDED", reason != null ? reason : "Task auto discarded", createdBy, createdAt, Instant.now()
        );
    }

    public BigDecimal actualCost() {
        return estimatedCost;
    }

    public BigDecimal usageTokens() {
        return usageQuantity;
    }
}
