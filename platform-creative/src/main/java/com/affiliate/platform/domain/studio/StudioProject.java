package com.affiliate.platform.domain.studio;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 长视频项目实体
 */
public record StudioProject(
        String id,
        String tenantId,
        String title,
        String brief,
        String aspectRatio,
        int durationSeconds,
        String targetPlatform,
        String adType,
        String creativeStyle,
        String modelCode,
        StudioProjectStatus status,
        String finalVideoUrl,
        String capcutDraftUrl,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static StudioProject create(
            String tenantId,
            String title,
            String brief,
            String aspectRatio,
            Integer durationSeconds,
            String targetPlatform,
            String adType,
            String creativeStyle,
            String modelCode,
            String createdBy
    ) {
        String id = "prj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant now = Instant.now();
        return new StudioProject(
                id,
                tenantId != null ? tenantId : "public",
                title != null ? title : "Untitled Project",
                brief != null ? brief : "",
                aspectRatio != null ? aspectRatio : "9:16",
                durationSeconds != null ? durationSeconds : 30,
                targetPlatform != null ? targetPlatform : "tiktok",
                adType != null ? adType : "ecommerce",
                creativeStyle != null ? creativeStyle : "live_action",
                modelCode != null ? modelCode : "ark_seedance_2_0",
                StudioProjectStatus.DRAFT,
                null,
                null,
                createdBy != null ? createdBy : "system",
                now,
                now
        );
    }

    public StudioProject withStatus(StudioProjectStatus newStatus) {
        return new StudioProject(
                id, tenantId, title, brief, aspectRatio, durationSeconds,
                targetPlatform, adType, creativeStyle, modelCode, newStatus,
                finalVideoUrl, capcutDraftUrl, createdBy, createdAt, Instant.now()
        );
    }

    public StudioProject withFinalArtifacts(String videoUrl, String draftUrl) {
        return new StudioProject(
                id, tenantId, title, brief, aspectRatio, durationSeconds,
                targetPlatform, adType, creativeStyle, modelCode, StudioProjectStatus.COMPLETED,
                videoUrl, draftUrl, createdBy, createdAt, Instant.now()
        );
    }
}
