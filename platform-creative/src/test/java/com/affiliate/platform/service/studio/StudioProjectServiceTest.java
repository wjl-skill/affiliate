package com.affiliate.platform.service.studio;

import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.ai.InMemoryAiGenerateTaskRecordRepository;
import com.affiliate.platform.repository.studio.InMemoryStudioAssetRepository;
import com.affiliate.platform.repository.studio.InMemoryStudioProjectRepository;
import com.affiliate.platform.repository.studio.InMemoryStudioSceneRepository;
import com.affiliate.platform.service.ai.AiVideoGenerationService;
import com.affiliate.platform.service.ai.generator.VideoGeneratorFactory;
import com.affiliate.platform.service.ai.generator.impl.LocalMockVideoGenerator;
import com.affiliate.platform.service.ai.meta.impl.AiVideoModelMetaServiceImpl;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import com.affiliate.platform.service.ai.provider.impl.LocalMockVideoProviderAdapter;
import com.affiliate.platform.service.studio.capcut.impl.CapCutMateClientImpl;
import com.affiliate.platform.service.studio.impl.StudioProjectServiceImpl;
import com.affiliate.platform.service.studio.pipeline.impl.StoryboardPipelineServiceImpl;
import com.affiliate.platform.service.studio.scene.impl.BoundaryKeyframeServiceImpl;
import com.affiliate.platform.web.studio.dto.StudioProjectCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StudioProjectService Tests - 7-Stage End-to-End Orchestration")
class StudioProjectServiceTest {

    private StudioProjectServiceImpl studioService;
    private InMemoryStudioProjectRepository projectRepo;
    private InMemoryStudioAssetRepository assetRepo;
    private InMemoryStudioSceneRepository sceneRepo;
    private List<DomainEvent<?>> publishedEvents;

    @BeforeEach
    void setUp() {
        projectRepo = new InMemoryStudioProjectRepository();
        assetRepo = new InMemoryStudioAssetRepository();
        sceneRepo = new InMemoryStudioSceneRepository();

        var pipelineService = new StoryboardPipelineServiceImpl();
        var keyframeService = new BoundaryKeyframeServiceImpl();
        var capcutMateClient = new CapCutMateClientImpl();

        // 绑定 Phase 2 的多模型视频生成引擎
        var metaService = new AiVideoModelMetaServiceImpl();
        var taskRepo = new InMemoryAiGenerateTaskRecordRepository();
        var providerFactory = new VideoProviderFactory(List.of(new LocalMockVideoProviderAdapter()));
        var generatorFactory = new VideoGeneratorFactory(List.of(new LocalMockVideoGenerator(providerFactory)));
        var aiVideoService = new AiVideoGenerationService(metaService, generatorFactory, providerFactory, taskRepo, e -> {});

        publishedEvents = new ArrayList<>();
        EventPublisher mockPublisher = publishedEvents::add;

        studioService = new StudioProjectServiceImpl(
                projectRepo, assetRepo, sceneRepo,
                pipelineService, keyframeService, capcutMateClient,
                aiVideoService, mockPublisher
        );
    }

    @Test
    @DisplayName("Should orchestrate full 7-stage long video pipeline successfully")
    void shouldOrchestrateFull7StagePipeline() {
        // ① 创建项目
        StudioProjectCreateRequest req = new StudioProjectCreateRequest(
                "全周期防脱固发实测",
                "针对职场年轻人熬夜脱发痛点，打卡28天，生出健康浓密小绒毛",
                "9:16", 30, "tiktok", "ecommerce", "live_action", "local_mock_video"
        );
        StudioProject project = studioService.createProject("tenant_alpha", req);
        assertNotNull(project);
        assertEquals(StudioProjectStatus.DRAFT, project.status());

        // ② 资产拆解
        List<StudioAsset> assets = studioService.analyzeAndSaveAssets("tenant_alpha", project.id());
        assertEquals(2, assets.size());
        assertEquals(2, studioService.listAssets("tenant_alpha", project.id()).size());

        // ④ 6管线分镜剧本规划
        List<StudioScene> scenes = studioService.generateStoryboards("tenant_alpha", project.id());
        assertFalse(scenes.isEmpty());
        assertEquals(StudioProjectStatus.SCRIPT_READY, studioService.getProject("tenant_alpha", project.id()).orElseThrow().status());

        // ⑤ 边界关键帧法绑定
        List<StudioScene> boundScenes = studioService.prepareBoundaryKeyframes("tenant_alpha", project.id());
        for (StudioScene s : boundScenes) {
            assertNotNull(s.startKeyframeUrl());
            assertNotNull(s.endKeyframeUrl());
        }

        // ⑥ 批量派发镜头片段生成 (联动 Phase 2 视频引擎)
        List<StudioScene> generatedScenes = studioService.generateSceneClips("tenant_alpha", project.id());
        for (StudioScene s : generatedScenes) {
            assertNotNull(s.videoClipUrl());
        }
        assertEquals(StudioProjectStatus.GENERATING, studioService.getProject("tenant_alpha", project.id()).orElseThrow().status());

        // ⑦ 导出剪映草稿工程
        CapCutDraftExportResult exportResult = studioService.exportCapCutDraft("tenant_alpha", project.id());
        assertNotNull(exportResult);
        assertTrue(exportResult.draftUrl().contains("draft_"));
        assertTrue(exportResult.capcutPackageDownloadUrl().endsWith("_capcut.zip"));

        StudioProject completed = studioService.getProject("tenant_alpha", project.id()).orElseThrow();
        assertEquals(StudioProjectStatus.COMPLETED, completed.status());
        assertNotNull(completed.finalVideoUrl());
        assertNotNull(completed.capcutDraftUrl());

        // 校验领域事件发布
        assertFalse(publishedEvents.isEmpty());
        DomainEvent<?> event = publishedEvents.get(0);
        assertEquals("creative.studio.project.exported.v1", event.eventType());
        assertEquals("tenant_alpha", event.tenantId());
        assertEquals(project.id(), event.aggregateId());
    }
}
