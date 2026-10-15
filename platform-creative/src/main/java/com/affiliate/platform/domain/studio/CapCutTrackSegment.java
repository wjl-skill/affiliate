package com.affiliate.platform.domain.studio;

import java.util.Map;

/**
 * 剪映草稿时间轴单轨道片段 (微秒高精刻度)
 */
public record CapCutTrackSegment(
        CapCutTrackType trackType,
        String sourceUrl,
        long startUs,
        long endUs,
        long durationUs,
        double volume,
        String text,
        Map<String, Object> extra
) {
    public static CapCutTrackSegment videoSegment(String videoUrl, long startUs, long durationUs) {
        return new CapCutTrackSegment(
                CapCutTrackType.VIDEO,
                videoUrl,
                startUs,
                startUs + durationUs,
                durationUs,
                1.0,
                null,
                Map.of()
        );
    }

    public static CapCutTrackSegment voiceoverSegment(String audioUrl, long startUs, long durationUs) {
        return new CapCutTrackSegment(
                CapCutTrackType.AUDIO_VOICEOVER,
                audioUrl,
                startUs,
                startUs + durationUs,
                durationUs,
                1.0,
                null,
                Map.of()
        );
    }

    public static CapCutTrackSegment bgmSegment(String bgmUrl, long startUs, long durationUs) {
        return new CapCutTrackSegment(
                CapCutTrackType.AUDIO_BGM,
                bgmUrl,
                startUs,
                startUs + durationUs,
                durationUs,
                0.35, // 降低 BGM 音量突出人声
                null,
                Map.of("fadeInDurationUs", 1_500_000L, "fadeOutDurationUs", 1_500_000L)
        );
    }

    public static CapCutTrackSegment subtitleSegment(String text, long startUs, long durationUs, Map<String, Object> style) {
        return new CapCutTrackSegment(
                CapCutTrackType.SUBTITLE,
                null,
                startUs,
                startUs + durationUs,
                durationUs,
                0.0,
                text,
                style != null ? style : Map.of()
        );
    }
}
