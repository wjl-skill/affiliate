package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 去水印算子：基于 FFmpeg Delogo 算法实现指定选区平滑虚化擦除
 */
@Component
public class WatermarkRemovalOperator extends AbstractVideoOperator {

    @Override
    public VideoProcessType operatorType() {
        return VideoProcessType.WATERMARK_REMOVE;
    }

    @Override
    public String buildFilterCommand(VideoProbeResult probe, Map<String, Object> config) {
        int x = getIntParam(config, "x", 0);
        int y = getIntParam(config, "y", 0);
        int w = getIntParam(config, "w", 200);
        int h = getIntParam(config, "h", 80);

        if (probe != null) {
            // 越界保护
            x = Math.max(0, Math.min(x, probe.width() - 1));
            y = Math.max(0, Math.min(y, probe.height() - 1));
            w = Math.max(1, Math.min(w, probe.width() - x));
            h = Math.max(1, Math.min(h, probe.height() - y));
        }

        return String.format("delogo=x=%d:y=%d:w=%d:h=%d:show=0", x, y, w, h);
    }

    @Override
    public Path apply(Path inputPath, Map<String, Object> config, Path workDir) throws Exception {
        Path outputPath = workDir.resolve("delogo_out.mp4");
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
