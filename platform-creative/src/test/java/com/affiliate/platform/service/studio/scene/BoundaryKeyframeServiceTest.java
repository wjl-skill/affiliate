package com.affiliate.platform.service.studio.scene;

import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.domain.studio.StudioScene;
import com.affiliate.platform.service.studio.pipeline.impl.StoryboardPipelineServiceImpl;
import com.affiliate.platform.service.studio.scene.impl.BoundaryKeyframeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BoundaryKeyframeService Tests - Boundary Keyframe Method")
class BoundaryKeyframeServiceTest {

    private BoundaryKeyframeServiceImpl keyframeService;
    private StoryboardPipelineServiceImpl pipelineService;

    @BeforeEach
    void setUp() {
        keyframeService = new BoundaryKeyframeServiceImpl();
        pipelineService = new StoryboardPipelineServiceImpl();
    }

    @Test
    @DisplayName("Should generate N+1 boundary keyframes for N scenes with seamless continuity")
    void shouldBindBoundaryKeyframesWithContinuity() {
        StudioProject project = StudioProject.create(
                "tenant_test", "电商项目", "测试简报",
                "9:16", 30, "tiktok", "ecommerce", "live_action", "ark_seedance_2_0", "creator"
        );

        List<StudioScene> scenes = pipelineService.generateStoryboardScenes(project);
        int n = scenes.size();
        assertTrue(n >= 3);

        List<StudioScene> boundScenes = keyframeService.bindBoundaryKeyframes(project, scenes);
        assertEquals(n, boundScenes.size());

        for (int i = 0; i < n; i++) {
            StudioScene s = boundScenes.get(i);
            assertNotNull(s.startKeyframeUrl(), "Scene " + (i + 1) + " must have startKeyframeUrl");
            assertNotNull(s.endKeyframeUrl(), "Scene " + (i + 1) + " must have endKeyframeUrl");

            // 关键连续性校验：第 i 镜的尾帧必须严格等于第 i+1 镜的首帧 (Ki)
            if (i < n - 1) {
                StudioScene nextScene = boundScenes.get(i + 1);
                assertEquals(s.endKeyframeUrl(), nextScene.startKeyframeUrl(),
                        "Continuity broken between scene " + (i + 1) + " end and scene " + (i + 2) + " start");
            }
        }
    }
}
