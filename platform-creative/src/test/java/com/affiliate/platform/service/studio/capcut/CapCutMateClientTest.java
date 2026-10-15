package com.affiliate.platform.service.studio.capcut;

import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.service.studio.capcut.impl.CapCutMateClientImpl;
import com.affiliate.platform.service.studio.pipeline.impl.StoryboardPipelineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CapCutMateClient Tests - Microsecond Timeline & Draft Export")
class CapCutMateClientTest {

    private CapCutMateClientImpl client;
    private StoryboardPipelineServiceImpl pipelineService;

    @BeforeEach
    void setUp() {
        client = new CapCutMateClientImpl();
        pipelineService = new StoryboardPipelineServiceImpl();
    }

    @Test
    @DisplayName("Should build 4-track timeline segments with accurate microsecond alignment")
    void shouldBuildTimelineTracksWithMicrosecondPrecision() {
        StudioProject project = StudioProject.create(
                "tenant_test", "电商带货项目", "测试简报",
                "9:16", 22, "tiktok", "ecommerce", "live_action", "ark_seedance_2_0", "creator"
        );
        List<StudioScene> scenes = pipelineService.generateStoryboardScenes(project);
        int totalExpectedSec = scenes.stream().mapToInt(StudioScene::durationSeconds).sum();
        long totalExpectedUs = totalExpectedSec * 1_000_000L;

        List<CapCutTrackSegment> segments = client.buildTimelineTracks(project, scenes);
        assertFalse(segments.isEmpty());

        // Verify video track segments
        List<CapCutTrackSegment> videoTracks = segments.stream()
                .filter(s -> s.trackType() == CapCutTrackType.VIDEO)
                .toList();
        assertEquals(scenes.size(), videoTracks.size());

        long runningUs = 0L;
        for (CapCutTrackSegment seg : videoTracks) {
            assertEquals(runningUs, seg.startUs());
            assertTrue(seg.durationUs() > 0);
            assertEquals(seg.startUs() + seg.durationUs(), seg.endUs());
            runningUs += seg.durationUs();
        }
        assertEquals(totalExpectedUs, runningUs);

        // Verify BGM track segment spans entire project duration
        CapCutTrackSegment bgm = segments.stream()
                .filter(s -> s.trackType() == CapCutTrackType.AUDIO_BGM)
                .findFirst().orElseThrow();
        assertEquals(0L, bgm.startUs());
        assertEquals(totalExpectedUs, bgm.endUs());
        assertEquals(0.35, bgm.volume());
        assertEquals(1_500_000L, bgm.extra().get("fadeInDurationUs"));
    }

    @Test
    @DisplayName("Should export structured draft JSON and draft URL")
    void shouldExportDraftJsonAndPackage() {
        StudioProject project = StudioProject.create(
                "tenant_test", "IAA小游戏", "游戏试玩",
                "9:16", 14, "tiktok", "gaming", "live_action", "ark_seedance_2_0", "creator"
        );
        List<StudioScene> scenes = pipelineService.generateStoryboardScenes(project);

        String json = client.generateDraftJson(project, scenes);
        assertNotNull(json);
        assertTrue(json.contains("draft_id"));
        assertTrue(json.contains("total_duration_us"));
        assertTrue(json.contains("segments"));

        CapCutDraftExportResult result = client.exportDraft(project, scenes);
        assertNotNull(result);
        assertEquals(project.id(), result.projectId());
        assertEquals(scenes.size(), result.totalScenes());
        assertTrue(result.draftUrl().contains("draft_"));
        assertTrue(result.capcutPackageDownloadUrl().endsWith("_capcut.zip"));
    }
}
