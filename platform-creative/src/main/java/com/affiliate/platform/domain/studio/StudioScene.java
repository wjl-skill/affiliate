package com.affiliate.platform.domain.studio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI 长视频工作台单分镜镜头实体
 */
public record StudioScene(
        String id,
        String tenantId,
        String projectId,
        int sceneIndex,
        int durationSeconds,
        String shotPrompt,
        String voiceoverText,
        String voiceoverTone,
        String voiceoverSpeed,
        String subtitleText,
        List<String> subtitleKeywords,
        String bgmCue,
        TransitionType transitionType,
        String transitionDesc,
        String startKeyframeUrl,
        String endKeyframeUrl,
        String videoClipUrl,
        String taskId,
        StudioSceneStatus status,
        Instant createdAt
) {
    public static StudioScene create(
            String tenantId,
            String projectId,
            int sceneIndex,
            int durationSeconds,
            String shotPrompt,
            String voiceoverText,
            String voiceoverTone,
            String voiceoverSpeed,
            String subtitleText,
            List<String> subtitleKeywords,
            String bgmCue,
            TransitionType transitionType,
            String transitionDesc
    ) {
        String id = "scn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // 时长约束保底 3 ~ 15 秒
        int safeDuration = Math.max(3, Math.min(durationSeconds, 15));
        return new StudioScene(
                id,
                tenantId != null ? tenantId : "public",
                projectId,
                sceneIndex,
                safeDuration,
                shotPrompt != null ? shotPrompt : "",
                voiceoverText != null ? voiceoverText : "",
                voiceoverTone != null ? voiceoverTone : "温暖女声",
                voiceoverSpeed != null ? voiceoverSpeed : "normal",
                subtitleText != null ? subtitleText : "",
                subtitleKeywords != null ? List.copyOf(subtitleKeywords) : List.of(),
                bgmCue != null ? bgmCue : "轻快节奏流行",
                transitionType != null ? transitionType : TransitionType.CHAIN_EXTEND,
                transitionDesc != null ? transitionDesc : "",
                null,
                null,
                null,
                null,
                StudioSceneStatus.DRAFT,
                Instant.now()
        );
    }

    public StudioScene withKeyframes(String startKeyframeUrl, String endKeyframeUrl) {
        return new StudioScene(
                id, tenantId, projectId, sceneIndex, durationSeconds,
                shotPrompt, voiceoverText, voiceoverTone, voiceoverSpeed,
                subtitleText, subtitleKeywords, bgmCue, transitionType, transitionDesc,
                startKeyframeUrl, endKeyframeUrl, videoClipUrl, taskId, status, createdAt
        );
    }

    public StudioScene toGenerating(String taskId) {
        return new StudioScene(
                id, tenantId, projectId, sceneIndex, durationSeconds,
                shotPrompt, voiceoverText, voiceoverTone, voiceoverSpeed,
                subtitleText, subtitleKeywords, bgmCue, transitionType, transitionDesc,
                startKeyframeUrl, endKeyframeUrl, videoClipUrl, taskId, StudioSceneStatus.GENERATING, createdAt
        );
    }

    public StudioScene toGenerated(String videoClipUrl) {
        return new StudioScene(
                id, tenantId, projectId, sceneIndex, durationSeconds,
                shotPrompt, voiceoverText, voiceoverTone, voiceoverSpeed,
                subtitleText, subtitleKeywords, bgmCue, transitionType, transitionDesc,
                startKeyframeUrl, endKeyframeUrl, videoClipUrl, taskId, StudioSceneStatus.GENERATED, createdAt
        );
    }

    public StudioScene toFailed() {
        return new StudioScene(
                id, tenantId, projectId, sceneIndex, durationSeconds,
                shotPrompt, voiceoverText, voiceoverTone, voiceoverSpeed,
                subtitleText, subtitleKeywords, bgmCue, transitionType, transitionDesc,
                startKeyframeUrl, endKeyframeUrl, videoClipUrl, taskId, StudioSceneStatus.FAILED, createdAt
        );
    }
}
