package com.affiliate.platform.service.studio.capcut.impl;

import com.affiliate.platform.domain.studio.CapCutDraftExportResult;
import com.affiliate.platform.domain.studio.CapCutTrackSegment;
import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.domain.studio.StudioScene;
import com.affiliate.platform.service.studio.capcut.CapCutMateClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 剪映/CapCut 官方草稿工程协议客户端实现：微秒级对齐与 4 轨道结构化标准 draft_content.json 生成
 */
@Service
public class CapCutMateClientImpl implements CapCutMateClient {

    private static final Logger log = LoggerFactory.getLogger(CapCutMateClientImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<CapCutTrackSegment> buildTimelineTracks(StudioProject project, List<StudioScene> scenes) {
        List<CapCutTrackSegment> segments = new ArrayList<>();
        if (scenes == null || scenes.isEmpty()) {
            return segments;
        }

        long cursorUs = 0L;
        for (StudioScene scene : scenes) {
            long durationUs = (long) scene.durationSeconds() * 1_000_000L;
            String videoUrl = scene.videoClipUrl() != null && !scene.videoClipUrl().isBlank()
                    ? scene.videoClipUrl()
                    : String.format("https://oss.affiliate.com/clips/%s_%d.mp4", project.id(), scene.sceneIndex());

            // 1. 主视频轨
            segments.add(CapCutTrackSegment.videoSegment(videoUrl, cursorUs, durationUs));

            // 2. 配音旁白轨
            String audioUrl = String.format("https://oss.affiliate.com/audio/tts_%s_%d.mp3", project.id(), scene.sceneIndex());
            segments.add(CapCutTrackSegment.voiceoverSegment(audioUrl, cursorUs, durationUs));

            // 4. 花字字幕轨
            String subText = scene.subtitleText() != null ? scene.subtitleText() : "";
            Map<String, Object> style = Map.of(
                    "font", "SourceHanSansCN-Bold",
                    "fontSize", 22,
                    "highlightKeywords", scene.subtitleKeywords() != null ? scene.subtitleKeywords() : List.of()
            );
            segments.add(CapCutTrackSegment.subtitleSegment(subText, cursorUs, durationUs, style));

            cursorUs += durationUs;
        }

        // 3. 背景音乐轨 (贯穿全局时长)
        if (cursorUs > 0) {
            String bgmUrl = String.format("https://oss.affiliate.com/bgm/library_%s.mp3", project.id());
            segments.add(CapCutTrackSegment.bgmSegment(bgmUrl, 0L, cursorUs));
        }

        return segments;
    }

    @Override
    public String generateDraftJson(StudioProject project, List<StudioScene> scenes) {
        List<CapCutTrackSegment> segments = buildTimelineTracks(project, scenes);
        long totalDurationUs = scenes != null
                ? scenes.stream().mapToLong(s -> (long) s.durationSeconds() * 1_000_000L).sum()
                : 0L;

        // 构建符合剪映/CapCut Desktop draft_content.json 标准规格的复合结构
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("draft_id", "draft_" + project.id());
        root.put("project_title", project.title());
        root.put("fps", 30.0);
        root.put("duration", totalDurationUs);
        root.put("total_duration_us", totalDurationUs);
        root.put("total_duration_seconds", (double) totalDurationUs / 1_000_000.0);

        // 画布尺寸配置
        int canvasWidth = 1080;
        int canvasHeight = 1920;
        if ("16:9".equalsIgnoreCase(project.aspectRatio())) {
            canvasWidth = 1920;
            canvasHeight = 1080;
        } else if ("1:1".equalsIgnoreCase(project.aspectRatio())) {
            canvasWidth = 1080;
            canvasHeight = 1080;
        }
        root.put("canvas_config", Map.of(
                "width", canvasWidth,
                "height", canvasHeight,
                "ratio", project.aspectRatio() != null ? project.aspectRatio() : "9:16"
        ));

        // 1. Materials 素材资源池
        Map<String, Object> materials = new LinkedHashMap<>();
        List<Map<String, Object>> videoMaterials = new ArrayList<>();
        List<Map<String, Object>> audioMaterials = new ArrayList<>();
        List<Map<String, Object>> textMaterials = new ArrayList<>();
        List<Map<String, Object>> speeds = new ArrayList<>();

        Map<String, Object> normalSpeed = new LinkedHashMap<>();
        normalSpeed.put("id", "speed_normal");
        normalSpeed.put("mode", 0);
        normalSpeed.put("speed", 1.0);
        speeds.add(normalSpeed);

        if (scenes != null) {
            for (StudioScene scn : scenes) {
                String matId = "mat_video_" + scn.id();
                long durUs = (long) scn.durationSeconds() * 1_000_000L;
                videoMaterials.add(Map.of(
                        "id", matId,
                        "path", scn.videoClipUrl() != null ? scn.videoClipUrl() : "",
                        "duration", durUs,
                        "width", canvasWidth,
                        "height", canvasHeight,
                        "type", "video"
                ));

                String audioMatId = "mat_audio_" + scn.id();
                audioMaterials.add(Map.of(
                        "id", audioMatId,
                        "path", "https://oss.affiliate.com/audio/tts_" + project.id() + "_" + scn.sceneIndex() + ".mp3",
                        "duration", durUs,
                        "type", "extract_music"
                ));

                String textMatId = "mat_text_" + scn.id();
                textMaterials.add(Map.of(
                        "id", textMatId,
                        "content", scn.subtitleText() != null ? scn.subtitleText() : "",
                        "font_title", "SourceHanSansCN-Bold",
                        "font_size", 22.0,
                        "text_color", "#FFFFFF",
                        "border_color", "#000000"
                ));
            }
        }
        materials.put("videos", videoMaterials);
        materials.put("audios", audioMaterials);
        materials.put("texts", textMaterials);
        materials.put("speeds", speeds);
        root.put("materials", materials);

        // 2. Tracks 四轨道定义 (Video, Audio Voiceover, Audio BGM, Subtitle Text)
        List<Map<String, Object>> tracks = new ArrayList<>();

        // 2.1 主视频轨 (Video Track)
        Map<String, Object> videoTrack = new LinkedHashMap<>();
        videoTrack.put("id", "track_video_0");
        videoTrack.put("type", "video");
        videoTrack.put("attribute", 0);
        videoTrack.put("flag", 0);

        List<Map<String, Object>> videoSegs = new ArrayList<>();
        long cursor = 0L;
        if (scenes != null) {
            for (StudioScene scn : scenes) {
                long durUs = (long) scn.durationSeconds() * 1_000_000L;
                Map<String, Object> seg = new LinkedHashMap<>();
                seg.put("id", "seg_v_" + scn.id());
                seg.put("material_id", "mat_video_" + scn.id());
                seg.put("speed_id", "speed_normal");
                seg.put("target_timerange", Map.of("start", cursor, "duration", durUs));
                seg.put("source_timerange", Map.of("start", 0L, "duration", durUs));
                videoSegs.add(seg);
                cursor += durUs;
            }
        }
        videoTrack.put("segments", videoSegs);
        tracks.add(videoTrack);

        // 2.2 配音旁白轨 (Voiceover Audio Track)
        Map<String, Object> voiceoverTrack = new LinkedHashMap<>();
        voiceoverTrack.put("id", "track_audio_voiceover_0");
        voiceoverTrack.put("type", "audio");
        voiceoverTrack.put("attribute", 0);
        voiceoverTrack.put("flag", 0);

        List<Map<String, Object>> voiceoverSegs = new ArrayList<>();
        long voiceoverCursor = 0L;
        if (scenes != null) {
            for (StudioScene scn : scenes) {
                long durUs = (long) scn.durationSeconds() * 1_000_000L;
                Map<String, Object> seg = new LinkedHashMap<>();
                seg.put("id", "seg_a_" + scn.id());
                seg.put("material_id", "mat_audio_" + scn.id());
                seg.put("speed_id", "speed_normal");
                seg.put("target_timerange", Map.of("start", voiceoverCursor, "duration", durUs));
                seg.put("source_timerange", Map.of("start", 0L, "duration", durUs));
                voiceoverSegs.add(seg);
                voiceoverCursor += durUs;
            }
        }
        voiceoverTrack.put("segments", voiceoverSegs);
        tracks.add(voiceoverTrack);

        // 2.3 背景音乐轨 (BGM Track)
        if (totalDurationUs > 0) {
            String bgmMatId = "mat_audio_bgm_" + project.id();
            audioMaterials.add(Map.of(
                    "id", bgmMatId,
                    "path", "https://oss.affiliate.com/bgm/library_" + project.id() + ".mp3",
                    "duration", totalDurationUs,
                    "type", "extract_music"
            ));

            Map<String, Object> bgmTrack = new LinkedHashMap<>();
            bgmTrack.put("id", "track_audio_bgm_0");
            bgmTrack.put("type", "audio");
            bgmTrack.put("attribute", 0);
            bgmTrack.put("flag", 0);

            Map<String, Object> bgmSeg = new LinkedHashMap<>();
            bgmSeg.put("id", "seg_bgm_" + project.id());
            bgmSeg.put("material_id", bgmMatId);
            bgmSeg.put("speed_id", "speed_normal");
            bgmSeg.put("target_timerange", Map.of("start", 0L, "duration", totalDurationUs));
            bgmSeg.put("source_timerange", Map.of("start", 0L, "duration", totalDurationUs));
            bgmTrack.put("segments", List.of(bgmSeg));
            tracks.add(bgmTrack);
        }

        // 2.4 字幕花字轨 (Subtitle Text Track)
        Map<String, Object> subtitleTrack = new LinkedHashMap<>();
        subtitleTrack.put("id", "track_text_subtitles_0");
        subtitleTrack.put("type", "text");
        subtitleTrack.put("attribute", 0);
        subtitleTrack.put("flag", 0);

        List<Map<String, Object>> subtitleSegs = new ArrayList<>();
        long subtitleCursor = 0L;
        if (scenes != null) {
            for (StudioScene scn : scenes) {
                long durUs = (long) scn.durationSeconds() * 1_000_000L;
                Map<String, Object> seg = new LinkedHashMap<>();
                seg.put("id", "seg_t_" + scn.id());
                seg.put("material_id", "mat_text_" + scn.id());
                seg.put("target_timerange", Map.of("start", subtitleCursor, "duration", durUs));
                subtitleSegs.add(seg);
                subtitleCursor += durUs;
            }
        }
        subtitleTrack.put("segments", subtitleSegs);
        tracks.add(subtitleTrack);

        root.put("tracks", tracks);
        root.put("segments", segments);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize draft JSON for project {}: {}", project.id(), e.getMessage());
            return "{}";
        }
    }

    @Override
    public CapCutDraftExportResult exportDraft(StudioProject project, List<StudioScene> scenes) {
        int totalScenes = scenes != null ? scenes.size() : 0;
        int totalDurationSeconds = scenes != null
                ? scenes.stream().mapToInt(StudioScene::durationSeconds).sum()
                : 0;

        String draftId = "draft_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String draftUrl = "https://draft.capcut-mate.internal/open?id=" + draftId;
        String packageDownloadUrl = String.format("https://oss.affiliate.com/drafts/%s_capcut.zip", project.id());

        log.info("Exported CapCut draft for project {}: scenes={}, duration={}s, draftUrl={}",
                project.id(), totalScenes, totalDurationSeconds, draftUrl);

        return new CapCutDraftExportResult(
                project.id(),
                totalScenes,
                totalDurationSeconds,
                draftUrl,
                packageDownloadUrl
        );
    }
}
