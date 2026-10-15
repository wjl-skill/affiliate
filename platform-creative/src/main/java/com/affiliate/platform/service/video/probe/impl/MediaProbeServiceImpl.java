package com.affiliate.platform.service.video.probe.impl;

import com.affiliate.platform.service.video.probe.MediaProbeService;

import com.affiliate.platform.domain.video.VideoProbeResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 媒体探针服务实现：优先调用系统底层 FFprobe，无外部工具时平滑降级至启发式仿真探针
 */
@Service
public class MediaProbeServiceImpl implements MediaProbeService {

    private static final Logger log = LoggerFactory.getLogger(MediaProbeServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public VideoProbeResult probe(Path videoPath) {
        if (videoPath == null) {
            return VideoProbeResult.defaultVertical();
        }
        return probe(videoPath.toAbsolutePath().toString());
    }

    @Override
    public VideoProbeResult probe(String videoUrlOrPath) {
        if (videoUrlOrPath == null || videoUrlOrPath.isBlank()) {
            return VideoProbeResult.defaultVertical();
        }

        // 尝试调用系统底层 FFprobe
        try {
            VideoProbeResult realProbe = runFfprobe(videoUrlOrPath);
            if (realProbe != null) {
                return realProbe;
            }
        } catch (Exception e) {
            log.debug("FFprobe execution unavailable or skipped for {}: {}", videoUrlOrPath, e.getMessage());
        }

        // 平滑降级：启发式推导
        return fallbackProbe(videoUrlOrPath);
    }

    private VideoProbeResult runFfprobe(String target) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    target
            );
            pb.redirectErrorStream(true);
            process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0 || sb.isEmpty()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(sb.toString());
            JsonNode streams = root.get("streams");
            JsonNode format = root.get("format");
            if (streams == null || !streams.isArray()) {
                return null;
            }

            int width = 0;
            int height = 0;
            double fps = 30.0;
            String videoCodec = "h264";
            int videoBitrate = 0;
            boolean hasAudio = false;
            String audioCodec = "aac";
            int audioSampleRate = 48000;
            int audioChannels = 2;

            for (JsonNode stream : streams) {
                String codecType = stream.path("codec_type").asText();
                if ("video".equalsIgnoreCase(codecType) && width == 0) {
                    width = stream.path("width").asInt(1080);
                    height = stream.path("height").asInt(1920);
                    videoCodec = stream.path("codec_name").asText("h264");
                    videoBitrate = stream.path("bit_rate").asInt(2500000);
                    String rFrameRate = stream.path("r_frame_rate").asText("30/1");
                    fps = parseFps(rFrameRate);
                } else if ("audio".equalsIgnoreCase(codecType)) {
                    hasAudio = true;
                    audioCodec = stream.path("codec_name").asText("aac");
                    audioSampleRate = stream.path("sample_rate").asInt(48000);
                    audioChannels = stream.path("channels").asInt(2);
                }
            }

            double duration = 0.0;
            if (format != null && format.has("duration")) {
                duration = format.path("duration").asDouble(0.0);
            }

            if (width <= 0 || height <= 0) {
                return null;
            }

            String aspectRatio = resolveAspectRatio(width, height);
            return new VideoProbeResult(
                    width, height, aspectRatio, duration, fps,
                    videoCodec, videoBitrate, hasAudio, audioCodec,
                    audioSampleRate, audioChannels
            );
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private double parseFps(String rFrameRate) {
        if (rFrameRate == null || rFrameRate.isBlank()) {
            return 30.0;
        }
        if (rFrameRate.contains("/")) {
            String[] parts = rFrameRate.split("/");
            try {
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                return den != 0 ? num / den : 30.0;
            } catch (Exception ignored) {}
        }
        try {
            return Double.parseDouble(rFrameRate);
        } catch (Exception ignored) {
            return 30.0;
        }
    }

    private VideoProbeResult fallbackProbe(String pathOrUrl) {
        String lower = pathOrUrl.toLowerCase();
        int width = 1080;
        int height = 1920;
        double duration = 15.0;

        if (lower.contains("1920x1080") || lower.contains("16_9") || lower.contains("horizontal") || lower.contains("landscape")) {
            width = 1920;
            height = 1080;
        } else if (lower.contains("1080x1080") || lower.contains("1_1") || lower.contains("square")) {
            width = 1080;
            height = 1080;
        } else if (lower.contains("720x1280")) {
            width = 720;
            height = 1280;
        } else if (lower.contains("4_5")) {
            width = 1080;
            height = 1350;
        }

        if (lower.contains("hook") || lower.contains("intro")) {
            duration = 3.5;
        } else if (lower.contains("main") || lower.contains("body")) {
            duration = 20.0;
        }

        String aspectRatio = resolveAspectRatio(width, height);
        return new VideoProbeResult(
                width,
                height,
                aspectRatio,
                duration,
                30.0,
                "h264",
                2500000,
                true,
                "aac",
                48000,
                2
        );
    }

    @Override
    public String resolveAspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "9:16";
        }
        double ratio = (double) width / height;

        if (Math.abs(ratio - (9.0 / 16.0)) < 0.05) {
            return "9:16";
        }
        if (Math.abs(ratio - (16.0 / 9.0)) < 0.05) {
            return "16:9";
        }
        if (Math.abs(ratio - 1.0) < 0.05) {
            return "1:1";
        }
        if (Math.abs(ratio - (4.0 / 5.0)) < 0.05) {
            return "4:5";
        }
        if (Math.abs(ratio - (3.0 / 4.0)) < 0.05) {
            return "3:4";
        }
        if (Math.abs(ratio - (4.0 / 3.0)) < 0.05) {
            return "4:3";
        }

        int gcd = gcd(width, height);
        int wRatio = width / gcd;
        int hRatio = height / gcd;
        return wRatio + ":" + hRatio;
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
