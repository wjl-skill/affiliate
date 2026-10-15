package com.affiliate.platform.domain.video;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 视频处理任务实体记录
 */
public record VideoProcessTaskRecord(
        String id,
        String tenantId,
        String taskNo,
        VideoProcessType processType,
        String sourceVideoUrl,
        String introVideoUrl,
        Integer targetWidth,
        Integer targetHeight,
        AlignMode alignMode,
        VideoProcessStatus status,
        Map<String, Object> parameters,
        String outputVideoUrl,
        String outputCoverUrl,
        Double durationSeconds,
        Long fileSizeBytes,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt
) {
    public static VideoProcessTaskRecord create(
            String tenantId,
            VideoProcessType processType,
            String sourceVideoUrl,
            String introVideoUrl,
            Integer targetWidth,
            Integer targetHeight,
            AlignMode alignMode,
            Map<String, Object> parameters
    ) {
        String id = UUID.randomUUID().toString();
        String taskNo = "vproc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new VideoProcessTaskRecord(
                id,
                tenantId != null ? tenantId : "public",
                taskNo,
                processType,
                sourceVideoUrl,
                introVideoUrl,
                targetWidth != null ? targetWidth : 1080,
                targetHeight != null ? targetHeight : 1920,
                alignMode != null ? alignMode : AlignMode.LETTERBOX,
                VideoProcessStatus.PENDING,
                parameters != null ? Map.copyOf(parameters) : Map.of(),
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null
        );
    }

    public VideoProcessTaskRecord toProcessing() {
        return new VideoProcessTaskRecord(
                id, tenantId, taskNo, processType, sourceVideoUrl, introVideoUrl,
                targetWidth, targetHeight, alignMode, VideoProcessStatus.PROCESSING,
                parameters, outputVideoUrl, outputCoverUrl, durationSeconds,
                fileSizeBytes, errorMessage, createdAt, finishedAt
        );
    }

    public VideoProcessTaskRecord toSuccess(String outputVideoUrl, String outputCoverUrl, Double durationSeconds, Long fileSizeBytes) {
        return new VideoProcessTaskRecord(
                id, tenantId, taskNo, processType, sourceVideoUrl, introVideoUrl,
                targetWidth, targetHeight, alignMode, VideoProcessStatus.SUCCESS,
                parameters, outputVideoUrl, outputCoverUrl, durationSeconds,
                fileSizeBytes, null, createdAt, Instant.now()
        );
    }

    public VideoProcessTaskRecord toFailed(String errorMessage) {
        return new VideoProcessTaskRecord(
                id, tenantId, taskNo, processType, sourceVideoUrl, introVideoUrl,
                targetWidth, targetHeight, alignMode, VideoProcessStatus.FAILED,
                parameters, outputVideoUrl, outputCoverUrl, durationSeconds,
                fileSizeBytes, errorMessage, createdAt, Instant.now()
        );
    }
}
