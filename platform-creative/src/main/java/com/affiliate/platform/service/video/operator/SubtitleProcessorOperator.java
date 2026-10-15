package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 智能字幕算子：支持 SRT 生成与 Libass 黑金描边高级样式硬压制
 */
@Component
public class SubtitleProcessorOperator extends AbstractVideoOperator {

    @Override
    public VideoProcessType operatorType() {
        return VideoProcessType.SUBTITLE;
    }

    @Override
    public String buildFilterCommand(VideoProbeResult probe, Map<String, Object> config) {
        String srtPath = getStringParam(config, "srtPath", "subs.srt").replace("\\", "/").replace(":", "\\:");
        String font = getStringParam(config, "fontName", "SourceHanSansCN-Bold");
        int fontSize = getIntParam(config, "fontSize", 22);
        String primaryColor = getStringParam(config, "primaryColour", "&H00FFFFFF");
        String outlineColor = getStringParam(config, "outlineColour", "&H00000000");
        int outline = getIntParam(config, "outline", 2);

        return String.format(
                "subtitles='%s':force_style='Fontname=%s,FontSize=%d,PrimaryColour=%s,OutlineColour=%s,Outline=%d'",
                srtPath, font, fontSize, primaryColor, outlineColor, outline
        );
    }

    @Override
    public Path apply(Path inputPath, Map<String, Object> config, Path workDir) throws Exception {
        Path outputPath = workDir.resolve("subtitle_out.mp4");

        // 提取或生成字幕文件
        Path srtFile = workDir.resolve("auto_subtitle.srt");
        String srtText = getStringParam(config, "srtText", null);
        if (srtText != null && !srtText.isBlank()) {
            Files.writeString(srtFile, srtText);
        } else {
            String rawSubtitle = getStringParam(config, "subtitleText", "Default Subtitle");
            String defaultSrt = String.format("1\n00:00:00,000 --> 00:00:10,000\n%s\n", rawSubtitle);
            Files.writeString(srtFile, defaultSrt);
        }

        // 构造带有工作区 srtFile 路径的参数配置
        Map<String, Object> enrichedConfig = new java.util.HashMap<>(config);
        enrichedConfig.put("srtPath", srtFile.toAbsolutePath().toString());

        String filter = buildFilterCommand(null, enrichedConfig);

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
