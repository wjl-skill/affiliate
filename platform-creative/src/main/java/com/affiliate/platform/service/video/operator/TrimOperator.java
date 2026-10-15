package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 分段无损裁剪算子 (Trim Operator)
 */
@Component
public class TrimOperator extends AbstractVideoOperator {

    @Override
    public VideoProcessType operatorType() {
        return VideoProcessType.TRIM;
    }

    @Override
    public String buildFilterCommand(VideoProbeResult probe, Map<String, Object> config) {
        double startSec = getDoubleParam(config, "startSeconds", 0.0);
        double endSec = getDoubleParam(config, "endSeconds", probe != null ? probe.durationSeconds() : 10.0);
        return String.format("-ss %s -to %s", formatTime(startSec), formatTime(endSec));
    }

    private String formatTime(double totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        double seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%06.3f", hours, minutes, seconds);
    }

    @Override
    public Path apply(Path inputPath, Map<String, Object> config, Path workDir) throws Exception {
        Path outputPath = workDir.resolve("trimmed_out.mp4");

        double startSec = getDoubleParam(config, "startSeconds", 0.0);
        double endSec = getDoubleParam(config, "endSeconds", 10.0);

        List<String> command = List.of(
                "ffmpeg", "-y",
                "-ss", formatTime(startSec),
                "-to", formatTime(endSec),
                "-i", inputPath.toAbsolutePath().toString(),
                "-c", "copy",
                outputPath.toAbsolutePath().toString()
        );

        return executeFfmpegOrFallback(command, inputPath, outputPath);
    }
}
