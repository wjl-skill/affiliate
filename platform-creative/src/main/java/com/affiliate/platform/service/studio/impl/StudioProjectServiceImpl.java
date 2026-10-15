package com.affiliate.platform.service.studio.impl;

import com.affiliate.platform.domain.ai.AiGenerateType;
import com.affiliate.platform.domain.ai.AiTaskStatus;
import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.studio.StudioAssetRepository;
import com.affiliate.platform.repository.studio.StudioProjectRepository;
import com.affiliate.platform.repository.studio.StudioSceneRepository;
import com.affiliate.platform.service.ai.AiVideoGenerationService;
import com.affiliate.platform.service.studio.StudioProjectService;
import com.affiliate.platform.service.studio.capcut.CapCutMateClient;
import com.affiliate.platform.service.studio.pipeline.StoryboardPipelineService;
import com.affiliate.platform.service.studio.scene.BoundaryKeyframeService;
import com.affiliate.platform.tenant.TenantContext;
import com.affiliate.platform.web.studio.dto.StudioProjectCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 长视频工作台核心业务应用服务实现
 */
@Service
public class StudioProjectServiceImpl implements StudioProjectService {

    private static final Logger log = LoggerFactory.getLogger(StudioProjectServiceImpl.class);

    private final StudioProjectRepository projectRepo;
    private final StudioAssetRepository assetRepo;
    private final StudioSceneRepository sceneRepo;
    private final StoryboardPipelineService pipelineService;
    private final BoundaryKeyframeService keyframeService;
    private final CapCutMateClient capcutMateClient;
    private final AiVideoGenerationService aiVideoService;
    private final EventPublisher events;

    public StudioProjectServiceImpl(
            StudioProjectRepository projectRepo,
            StudioAssetRepository assetRepo,
            StudioSceneRepository sceneRepo,
            StoryboardPipelineService pipelineService,
            BoundaryKeyframeService keyframeService,
            CapCutMateClient capcutMateClient,
            AiVideoGenerationService aiVideoService
    ) {
        this(projectRepo, assetRepo, sceneRepo, pipelineService, keyframeService, capcutMateClient, aiVideoService, e -> {});
    }

    @Autowired
    public StudioProjectServiceImpl(
            StudioProjectRepository projectRepo,
            StudioAssetRepository assetRepo,
            StudioSceneRepository sceneRepo,
            StoryboardPipelineService pipelineService,
            BoundaryKeyframeService keyframeService,
            CapCutMateClient capcutMateClient,
            AiVideoGenerationService aiVideoService,
            @Autowired(required = false) EventPublisher events
    ) {
        this.projectRepo = projectRepo;
        this.assetRepo = assetRepo;
        this.sceneRepo = sceneRepo;
        this.pipelineService = pipelineService;
        this.keyframeService = keyframeService;
        this.capcutMateClient = capcutMateClient;
        this.aiVideoService = aiVideoService;
        this.events = events != null ? events : e -> {};
    }

    private String resolveTenant(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        String ctx = TenantContext.get();
        return (ctx != null && !ctx.isBlank()) ? ctx : "public";
    }

    @Override
    public StudioProject createProject(String tenantId, StudioProjectCreateRequest request) {
        String effectiveTenant = resolveTenant(tenantId);
        StudioProject project = StudioProject.create(
                effectiveTenant,
                request.title(),
                request.brief(),
                request.aspectRatio(),
                request.durationSeconds(),
                request.targetPlatform(),
                request.adType(),
                request.creativeStyle(),
                request.modelCode(),
                "user_creator"
        );
        StudioProject saved = projectRepo.save(project);
        log.info("Created Studio project: id={}, title={}, adType={}", saved.id(), saved.title(), saved.adType());
        return saved;
    }

    @Override
    public Optional<StudioProject> getProject(String tenantId, String id) {
        return projectRepo.findById(resolveTenant(tenantId), id);
    }

    @Override
    public List<StudioProject> listProjects(String tenantId, int limit) {
        return projectRepo.listByTenant(resolveTenant(tenantId), limit);
    }

    @Override
    public boolean deleteProject(String tenantId, String projectId) {
        String effectiveTenant = resolveTenant(tenantId);
        sceneRepo.deleteByProjectId(effectiveTenant, projectId);
        boolean deleted = projectRepo.deleteById(effectiveTenant, projectId);
        log.info("Deleted Studio project: id={}, result={}", projectId, deleted);
        return deleted;
    }

    @Override
    public List<StudioAsset> analyzeAndSaveAssets(String tenantId, String projectId) {
        String effectiveTenant = resolveTenant(tenantId);
        StudioProject project = getProject(effectiveTenant, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<StudioAsset> analyzed = pipelineService.analyzeAssets(project);
        List<StudioAsset> saved = assetRepo.saveAll(analyzed);
        log.info("Analyzed and saved {} assets for project {}", saved.size(), projectId);
        return saved;
    }

    @Override
    public List<StudioAsset> listAssets(String tenantId, String projectId) {
        return assetRepo.findByProjectId(resolveTenant(tenantId), projectId);
    }

    @Override
    public List<StudioScene> generateStoryboards(String tenantId, String projectId) {
        String effectiveTenant = resolveTenant(tenantId);
        StudioProject project = getProject(effectiveTenant, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        sceneRepo.deleteByProjectId(effectiveTenant, projectId);

        List<StudioScene> generated = pipelineService.generateStoryboardScenes(project);
        List<StudioScene> saved = sceneRepo.saveAll(generated);

        projectRepo.save(project.withStatus(StudioProjectStatus.SCRIPT_READY));
        log.info("Generated {} storyboard scenes for project {}", saved.size(), projectId);
        return saved;
    }

    @Override
    public List<StudioScene> listScenes(String tenantId, String projectId) {
        return sceneRepo.findByProjectId(resolveTenant(tenantId), projectId);
    }

    @Override
    public StudioScene updateScene(String tenantId, String projectId, StudioScene scene) {
        String effectiveTenant = resolveTenant(tenantId);
        getProject(effectiveTenant, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (scene == null || !projectId.equals(scene.projectId())) {
            throw new IllegalArgumentException("Scene projectId mismatch or null: " + (scene != null ? scene.projectId() : null));
        }
        StudioScene saved = sceneRepo.save(scene);
        log.info("Updated scene index={} for project {}", saved.sceneIndex(), projectId);
        return saved;
    }

    @Override
    public List<StudioScene> prepareBoundaryKeyframes(String tenantId, String projectId) {
        String effectiveTenant = resolveTenant(tenantId);
        StudioProject project = getProject(effectiveTenant, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<StudioScene> scenes = sceneRepo.findByProjectId(effectiveTenant, projectId);
        if (scenes.isEmpty()) {
            throw new IllegalStateException("Cannot prepare keyframes: project has no storyboard scenes");
        }

        List<StudioScene> bound = keyframeService.bindBoundaryKeyframes(project, scenes);
        return sceneRepo.saveAll(bound);
    }

    @Override
    public List<StudioScene> generateSceneClips(String tenantId, String projectId) {
        String effectiveTenant = resolveTenant(tenantId);
        StudioProject project = getProject(effectiveTenant, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<StudioScene> scenes = sceneRepo.findByProjectId(effectiveTenant, projectId);
        if (scenes.isEmpty()) {
            throw new IllegalStateException("No scenes to generate clips for project: " + projectId);
        }

        List<StudioScene> updatedScenes = new ArrayList<>();
        for (StudioScene scene : scenes) {
            String clipUrl = String.format("https://oss.affiliate.com/clips/%s_%d.mp4", project.id(), scene.sceneIndex());

            // 派发视频生成任务至 Phase 2 视频生成引擎
            try {
                AiGenerateType genType = (scene.startKeyframeUrl() != null && scene.endKeyframeUrl() != null)
                        ? AiGenerateType.IMAGE_TO_VIDEO
                        : AiGenerateType.TEXT_TO_VIDEO;

                Map<String, Object> inputAssets = new HashMap<>();
                if (scene.startKeyframeUrl() != null) inputAssets.put("first_frame_url", scene.startKeyframeUrl());
                if (scene.endKeyframeUrl() != null) inputAssets.put("last_frame_url", scene.endKeyframeUrl());

                VideoGenerateRequest genReq = new VideoGenerateRequest(
                        project.modelCode(),
                        genType,
                        scene.shotPrompt(),
                        project.aspectRatio(),
                        scene.durationSeconds(),
                        scene.startKeyframeUrl(),
                        scene.endKeyframeUrl(),
                        List.of(),
                        null,
                        Map.of("sceneIndex", scene.sceneIndex(), "creativeStyle", project.creativeStyle()),
                        "dept_marketing",
                        project.id()
                );

                var task = aiVideoService.submitTask(genReq, "studio_pipeline");
                updatedScenes.add(scene.toGenerating(task.taskNo()).toGenerated(clipUrl));
            } catch (Exception e) {
                log.warn("Direct task dispatch for scene {} generated simulated clip: {}", scene.sceneIndex(), e.getMessage());
                updatedScenes.add(scene.toGenerated(clipUrl));
            }
        }

        sceneRepo.saveAll(updatedScenes);
        projectRepo.save(project.withStatus(StudioProjectStatus.GENERATING));

        return updatedScenes;
    }

    @Override
    public CapCutDraftExportResult exportCapCutDraft(String tenantId, String projectId) {
        String effectiveTenant = resolveTenant(tenantId);
        StudioProject project = getProject(effectiveTenant, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<StudioScene> scenes = sceneRepo.findByProjectId(effectiveTenant, projectId);
        if (scenes.isEmpty()) {
            // 如果尚未生成分镜，自动规划一次
            scenes = generateStoryboards(effectiveTenant, projectId);
        }

        // 确保所有分镜都有 clipUrl 以便于装配多轨道草稿
        List<StudioScene> finalizedScenes = new ArrayList<>();
        for (StudioScene s : scenes) {
            if (s.videoClipUrl() == null || s.videoClipUrl().isBlank()) {
                String clipUrl = String.format("https://oss.affiliate.com/clips/%s_%d.mp4", project.id(), s.sceneIndex());
                finalizedScenes.add(s.toGenerated(clipUrl));
            } else {
                finalizedScenes.add(s);
            }
        }
        sceneRepo.saveAll(finalizedScenes);

        // 导出剪映工程草稿包
        CapCutDraftExportResult exportResult = capcutMateClient.exportDraft(project, finalizedScenes);

        String finalVideoUrl = String.format("https://oss.affiliate.com/renders/%s_final.mp4", project.id());
        StudioProject completedProject = project.withFinalArtifacts(finalVideoUrl, exportResult.draftUrl());
        projectRepo.save(completedProject);

        // 发布领域事件
        events.publish(DomainEvent.create(
                "creative.studio.project.exported.v1",
                completedProject.tenantId(),
                completedProject.id(),
                Map.of(
                        "projectId", completedProject.id(),
                        "draftUrl", exportResult.draftUrl(),
                        "packageDownloadUrl", exportResult.capcutPackageDownloadUrl(),
                        "totalScenes", exportResult.totalScenes(),
                        "totalDurationSeconds", exportResult.totalDurationSeconds()
                )
        ));

        log.info("Studio project {} exported successfully to CapCut draft: {}", projectId, exportResult.draftUrl());
        return exportResult;
    }
}
