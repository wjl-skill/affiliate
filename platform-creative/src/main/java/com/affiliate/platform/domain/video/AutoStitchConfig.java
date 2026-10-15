package com.affiliate.platform.domain.video;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 批量混剪配置实体
 */
public record AutoStitchConfig(
        String id,
        String tenantId,
        String configName,
        List<String> introVideoUrls,
        List<String> bodyVideoUrls,
        String targetAspectRatio,
        AlignMode alignMode,
        Map<String, Object> ctaConfig,
        Instant scheduledTime,
        AutoStitchStatus status,
        Instant createdAt
) {
    public static AutoStitchConfig create(
            String tenantId,
            String configName,
            List<String> introVideoUrls,
            List<String> bodyVideoUrls,
            String targetAspectRatio,
            AlignMode alignMode,
            Map<String, Object> ctaConfig,
            Instant scheduledTime
    ) {
        return new AutoStitchConfig(
                UUID.randomUUID().toString(),
                tenantId != null ? tenantId : "public",
                configName,
                introVideoUrls != null ? List.copyOf(introVideoUrls) : List.of(),
                bodyVideoUrls != null ? List.copyOf(bodyVideoUrls) : List.of(),
                targetAspectRatio != null ? targetAspectRatio : "9:16",
                alignMode != null ? alignMode : AlignMode.LETTERBOX,
                ctaConfig != null ? Map.copyOf(ctaConfig) : Map.of(),
                scheduledTime,
                AutoStitchStatus.PENDING,
                Instant.now()
        );
    }

    public AutoStitchConfig markExecuted() {
        return new AutoStitchConfig(
                id, tenantId, configName, introVideoUrls, bodyVideoUrls,
                targetAspectRatio, alignMode, ctaConfig, scheduledTime,
                AutoStitchStatus.EXECUTED, createdAt
        );
    }

    public AutoStitchConfig markCancelled() {
        return withStatus(AutoStitchStatus.CANCELLED);
    }

    public AutoStitchConfig withStatus(AutoStitchStatus newStatus) {
        return new AutoStitchConfig(
                id, tenantId, configName, introVideoUrls, bodyVideoUrls,
                targetAspectRatio, alignMode, ctaConfig, scheduledTime,
                newStatus, createdAt
        );
    }
}
