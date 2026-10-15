package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 行动呼吁动效贴片算子 (CTA Overlay)
 */
@Component
public class CtaOverlayOperator extends AbstractVideoOperator {

    @Override
    public VideoProcessType operatorType() {
        return VideoProcessType.CTA_OVERLAY;
    }

    @Override
    public String buildFilterCommand(VideoProbeResult probe, Map<String, Object> config) {
        double duration = probe != null ? probe.durationSeconds() : getDoubleParam(config, "videoDuration", 30.0);
        double offsetFromEnd = getDoubleParam(config, "offsetFromEndSeconds", 3.5);
        double startTime = Math.max(0.0, duration - offsetFromEnd);
        double fadeInDuration = getDoubleParam(config, "fadeInDuration", 0.5);

        String ctaImageUrl = getStringParam(config, "ctaImageUrl", null);
        if (ctaImageUrl != null && !ctaImageUrl.isBlank()) {
            return String.format(
                    "[1:v]format=rgba,fade=in:st=%.2f:d=%.2f:alpha=1[cta];[0:v][cta]overlay=(W-w)/2:H-h-120:enable='gte(t,%.2f)'",
                    startTime, fadeInDuration, startTime
            );
        }

        String ctaText = getStringParam(config, "ctaText", "点击立即下载");
        return String.format(
                "drawtext=text='%s':fontcolor=white:fontsize=36:box=1:boxcolor=red@0.8:boxborderw=10:x=(w-text_w)/2:y=h-text_h-120:enable='gte(t,%.2f)'",
                ctaText, startTime
        );
    }

    @Override
    public Path apply(Path inputPath, Map<String, Object> config, Path workDir) throws Exception {
        Path outputPath = workDir.resolve("cta_overlay_out.mp4");
        String filter = buildFilterCommand(null, config);

        List<String> command = List.of(
                "ffmpeg", "-y",
                "-i", inputPath.toAbsolutePath().toString(),
                "-vf", filter,
                "-c:a", "copy",
                outputPath.toAbsolutePath().toString()
        );

        return executeFfmpegOrFallback(command, inputPath, outputPath);
    }
}
