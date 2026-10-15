package com.affiliate.platform.web.studio;

import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.repository.ai.InMemoryAiGenerateTaskRecordRepository;
import com.affiliate.platform.repository.studio.InMemoryDigitalHumanAvatarRepository;
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
import com.affiliate.platform.service.studio.human.impl.DigitalHumanDriverServiceImpl;
import com.affiliate.platform.service.studio.impl.StudioProjectServiceImpl;
import com.affiliate.platform.service.studio.pipeline.impl.StoryboardPipelineServiceImpl;
import com.affiliate.platform.service.studio.scene.impl.BoundaryKeyframeServiceImpl;
import com.affiliate.platform.web.studio.dto.DigitalHumanDriveRequest;
import com.affiliate.platform.web.studio.dto.StudioProjectCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StudioProjectController REST Endpoints Tests")
class StudioProjectControllerTest {

    private StudioProjectController controller;
    private StudioProjectServiceImpl studioService;
    private DigitalHumanDriverServiceImpl digitalHumanService;

    @BeforeEach
    void setUp() {
        var projectRepo = new InMemoryStudioProjectRepository();
        var assetRepo = new InMemoryStudioAssetRepository();
        var sceneRepo = new InMemoryStudioSceneRepository();
        var avatarRepo = new InMemoryDigitalHumanAvatarRepository();

        var pipelineService = new StoryboardPipelineServiceImpl();
        var keyframeService = new BoundaryKeyframeServiceImpl();
        var capcutMateClient = new CapCutMateClientImpl();

        var metaService = new AiVideoModelMetaServiceImpl();
        var taskRepo = new InMemoryAiGenerateTaskRecordRepository();
        var providerFactory = new VideoProviderFactory(List.of(new LocalMockVideoProviderAdapter()));
        var generatorFactory = new VideoGeneratorFactory(List.of(new LocalMockVideoGenerator(providerFactory)));
        var aiVideoService = new AiVideoGenerationService(metaService, generatorFactory, providerFactory, taskRepo, e -> {});

        studioService = new StudioProjectServiceImpl(
                projectRepo, assetRepo, sceneRepo,
                pipelineService, keyframeService, capcutMateClient,
                aiVideoService, e -> {}
        );
        digitalHumanService = new DigitalHumanDriverServiceImpl(avatarRepo);

        controller = new StudioProjectController(studioService, digitalHumanService);
    }

    @Test
    @DisplayName("Should execute all project lifecycle REST endpoints")
    void testProjectEndpoints() {
        // 1. POST /projects
        StudioProjectCreateRequest req = new StudioProjectCreateRequest(
                "新晋奶茶店探店",
                "第一视角打卡，实测招牌生酪厚乳茶，领券立减15元",
                "9:16", 25, "tiktok", "local_life", "live_action", "local_mock_video"
        );
        StudioProject created = controller.createProject(req);
        assertNotNull(created);
        assertNotNull(created.id());

        // 2. GET /projects/{id}
        StudioProject fetched = controller.getProject(created.id());
        assertEquals(created.id(), fetched.id());

        // 3. GET /projects
        List<StudioProject> list = controller.listProjects(10);
        assertFalse(list.isEmpty());

        // 4. POST /projects/{id}/assets/analyze
        List<StudioAsset> assets = controller.analyzeAssets(created.id());
        assertFalse(assets.isEmpty());

        // 5. GET /projects/{id}/assets
        List<StudioAsset> queriedAssets = controller.listAssets(created.id());
        assertEquals(assets.size(), queriedAssets.size());

        // 6. POST /projects/{id}/storyboards/generate
        List<StudioScene> scenes = controller.generateStoryboards(created.id());
        assertFalse(scenes.isEmpty());

        // 7. GET /projects/{id}/scenes
        List<StudioScene> queriedScenes = controller.listScenes(created.id());
        assertEquals(scenes.size(), queriedScenes.size());

        // 8. POST /projects/{id}/keyframes/prepare
        List<StudioScene> boundScenes = controller.prepareBoundaryKeyframes(created.id());
        assertNotNull(boundScenes.get(0).startKeyframeUrl());

        // 9. POST /projects/{id}/scenes/generate-all
        List<StudioScene> generatedScenes = controller.generateSceneClips(created.id());
        assertNotNull(generatedScenes.get(0).videoClipUrl());

        // 10. POST /projects/{id}/export-capcut
        CapCutDraftExportResult exportResult = controller.exportCapCutDraft(created.id());
        assertNotNull(exportResult);
        assertTrue(exportResult.draftUrl().contains("draft_"));
    }

    @Test
    @DisplayName("Should drive digital human and list avatars via REST endpoints")
    void testDigitalHumanEndpoints() {
        List<DigitalHumanAvatar> avatars = controller.listDigitalHumanAvatars();
        assertFalse(avatars.isEmpty());

        DigitalHumanDriveRequest req = new DigitalHumanDriveRequest(
                avatars.get(0).id(),
                "欢迎光临直播间，今天带来超值骨折福利！",
                "zh_female_shuangkuai",
                "normal"
        );

        DigitalHumanDriveResult result = controller.driveDigitalHuman(req);
        assertNotNull(result);
        assertEquals("SUCCESS", result.status());
        assertNotNull(result.videoUrl());
    }

    @Test
    @DisplayName("Should update scene and delete project via REST endpoints")
    void testUpdateSceneAndDeleteProject() {
        StudioProjectCreateRequest req = new StudioProjectCreateRequest(
                "待删除项目", "测试删除与更新", "16:9", 15, "youtube", "ecommerce", "3d_animation", "local_mock_video"
        );
        StudioProject project = controller.createProject(req);
        assertNotNull(project);

        List<StudioScene> scenes = controller.generateStoryboards(project.id());
        assertFalse(scenes.isEmpty());
        StudioScene first = scenes.get(0);

        // Update scene narration & shotPrompt
        StudioScene modified = new StudioScene(
                first.id(), first.tenantId(), first.projectId(), first.sceneIndex(),
                first.durationSeconds(), "全新特写运镜", "全新爆款口播词",
                first.voiceoverTone(), first.voiceoverSpeed(), "更新字幕",
                first.subtitleKeywords(), first.bgmCue(), first.transitionType(),
                first.transitionDesc(), first.startKeyframeUrl(), first.endKeyframeUrl(),
                first.videoClipUrl(), first.taskId(), first.status(), first.createdAt()
        );

        StudioScene updated = controller.updateScene(project.id(), first.id(), modified);
        assertEquals("全新爆款口播词", updated.voiceoverText());
        assertEquals("全新特写运镜", updated.shotPrompt());

        // Delete project
        controller.deleteProject(project.id());
        assertThrows(IllegalArgumentException.class, () -> controller.getProject(project.id()));
    }
}
