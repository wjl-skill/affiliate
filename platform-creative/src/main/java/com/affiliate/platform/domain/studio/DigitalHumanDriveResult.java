package com.affiliate.platform.domain.studio;

/**
 * 数字人唇形渲染驱动结果
 */
public record DigitalHumanDriveResult(
        String taskNo,
        String status,
        String audioUrl,
        double audioDurationSeconds,
        String videoUrl,
        String message
) {}
