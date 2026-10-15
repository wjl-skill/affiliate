package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 片头硬拼接与画幅自适应对齐算子
 */
@Component
public class IntroSplicingOperator extends AbstractVideoOperator {

    @Override
    public VideoProcessType operatorType() {
        return VideoProcessType.INTRO_STITCH;
    }

    @Override
    public String buildFilterCommand(VideoProbeResult probe, Map<String, Object> config) {
        int targetWidth = getIntParam(config, "targetWidth", 1080);
        int targetHeight = getIntParam(config, "targetHeight", 1920);
        String alignModeStr = getStringParam(config, "alignMode", "LETTERBOX");
        AlignMode alignMode;
        try {
            alignMode = AlignMode.valueOf(alignModeStr);
        } catch (Exception e) {
            alignMode = AlignMode.LETTERBOX;
        }

        return buildScalePadFilter(targetWidth, targetHeight, alignMode);
    }

    public String buildScalePadFilter(int targetWidth, int targetHeight, AlignMode alignMode) {
        return switch (alignMode) {
            case CROP -> String.format("scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d",
                    targetWidth, targetHeight, targetWidth, targetHeight);
            case SCALE -> String.format("scale=%d:%d", targetWidth, targetHeight);
            case LETTERBOX -> String.format("scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2:black",
                    targetWidth, targetHeight, targetWidth, targetHeight);
        };
    }

    @Override
    public Path apply(Path inputPath, Map<String, Object> config, Path workDir) throws Exception {
        Path outputPath = workDir.resolve("stitched_out.mp4");

        String introPathStr = getStringParam(config, "introPath", null);
        if (introPathStr == null) {
            introPathStr = getStringParam(config, "introVideoUrl", null);
        }

        Path introPath = null;
        if (introPathStr != null) {
            introPath = Paths.get(introPathStr);
        }

        // 若片头不存在或未指定，创建仿真片头
        if (introPath == null || !Files.exists(introPath)) {
            introPath = workDir.resolve("simulated_intro_hook.mp4");
            if (!Files.exists(introPath)) {
                Files.writeString(introPath, "simulated_intro_hook_stream");
            }
        }

        int targetWidth = getIntParam(config, "targetWidth", 1080);
        int targetHeight = getIntParam(config, "targetHeight", 1920);
        String scaleFilter = buildFilterCommand(null, config);

        // FFmpeg 复合滤镜：画幅自适应 + 音频 48kHz 重采样 + Concat 拼接
        String filterComplex = String.format(
                "[0:v]%s,setsar=1[v0]; [0:a]aformat=sample_rates=48000:channel_layouts=stereo[a0]; " +
                        "[1:v]%s,setsar=1[v1]; [1:a]aformat=sample_rates=48000:channel_layouts=stereo[a1]; " +
                        "[v0][a0][v1][a1]concat=n=2:v=1:a=1[outv][outa]",
                scaleFilter, scaleFilter
        );

        List<String> command = List.of(
                "ffmpeg", "-y",
                "-i", introPath.toAbsolutePath().toString(),
                "-i", inputPath.toAbsolutePath().toString(),
                "-filter_complex", filterComplex,
                "-map", "[outv]",
                "-map", "[outa]",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                outputPath.toAbsolutePath().toString()
        );

        return executeFfmpegOrFallback(command, inputPath, outputPath);
    }
}
