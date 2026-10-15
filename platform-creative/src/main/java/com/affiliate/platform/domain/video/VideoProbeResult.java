package com.affiliate.platform.domain.video;

/**
 * 视频探针元数据提取结果
 */
public record VideoProbeResult(
        int width,
        int height,
        String aspectRatio,
        double durationSeconds,
        double fps,
        String videoCodec,
        int videoBitrate,
        boolean hasAudio,
        String audioCodec,
        int audioSampleRate,
        int audioChannels
) {
    public static VideoProbeResult defaultVertical() {
        return new VideoProbeResult(1080, 1920, "9:16", 15.0, 30.0, "h264", 2500000, true, "aac", 48000, 2);
    }

    public static VideoProbeResult defaultHorizontal() {
        return new VideoProbeResult(1920, 1080, "16:9", 15.0, 30.0, "h264", 2500000, true, "aac", 48000, 2);
    }

    public static VideoProbeResult defaultSquare() {
        return new VideoProbeResult(1080, 1080, "1:1", 15.0, 30.0, "h264", 2500000, true, "aac", 48000, 2);
    }
}
