package com.affiliate.platform.web.studio;

import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.service.studio.StudioProjectService;
import com.affiliate.platform.service.studio.human.DigitalHumanDriverService;
import com.affiliate.platform.tenant.TenantContext;
import com.affiliate.platform.web.studio.dto.DigitalHumanDriveRequest;
import com.affiliate.platform.web.studio.dto.StudioProjectCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 长视频工作台与剪映草稿导出 REST 控制器
 */
@RestController
@RequestMapping("/api/v1/creative/studio")
public class StudioProjectController {

    private final StudioProjectService studioService;
    private final DigitalHumanDriverService digitalHumanService;

    public StudioProjectController(
            StudioProjectService studioService,
            DigitalHumanDriverService digitalHumanService
    ) {
        this.studioService = studioService;
        this.digitalHumanService = digitalHumanService;
    }

    /**
     * 创建长视频项目
     * POST /api/v1/creative/studio/projects
     */
    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioProject createProject(@Valid @RequestBody StudioProjectCreateRequest request) {
        String tenant = TenantContext.get();
        return studioService.createProject(tenant, request);
    }

    /**
     * 查询项目详情
     * GET /api/v1/creative/studio/projects/{id}
     */
    @GetMapping("/projects/{id}")
    public StudioProject getProject(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.getProject(tenant, id)
                .orElseThrow(() -> new IllegalArgumentException("Studio project not found: " + id));
    }

    /**
     * 查询近期项目列表
     * GET /api/v1/creative/studio/projects
     */
    @GetMapping("/projects")
    public List<StudioProject> listProjects(@RequestParam(defaultValue = "20") int limit) {
        String tenant = TenantContext.get();
        return studioService.listProjects(tenant, limit);
    }

    /**
     * 删除长视频项目
     * DELETE /api/v1/creative/studio/projects/{id}
     */
    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable String id) {
        String tenant = TenantContext.get();
        boolean deleted = studioService.deleteProject(tenant, id);
        if (!deleted) {
            throw new IllegalArgumentException("Project not found or unable to delete: " + id);
        }
    }

    /**
     * 智能拆解并生成角色与核心商品资产
     * POST /api/v1/creative/studio/projects/{id}/assets/analyze
     */
    @PostMapping("/projects/{id}/assets/analyze")
    public List<StudioAsset> analyzeAssets(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.analyzeAndSaveAssets(tenant, id);
    }

    /**
     * 查询项目资产列表
     * GET /api/v1/creative/studio/projects/{id}/assets
     */
    @GetMapping("/projects/{id}/assets")
    public List<StudioAsset> listAssets(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.listAssets(tenant, id);
    }

    /**
     * 依据 6 套行业管线规划分镜剧本
     * POST /api/v1/creative/studio/projects/{id}/storyboards/generate
     */
    @PostMapping("/projects/{id}/storyboards/generate")
    public List<StudioScene> generateStoryboards(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.generateStoryboards(tenant, id);
    }

    /**
     * 查询项目分镜列表
     * GET /api/v1/creative/studio/projects/{id}/scenes
     */
    @GetMapping("/projects/{id}/scenes")
    public List<StudioScene> listScenes(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.listScenes(tenant, id);
    }

    /**
     * 更新项目指定分镜镜头
     * PUT /api/v1/creative/studio/projects/{id}/scenes/{sceneId}
     */
    @PutMapping("/projects/{id}/scenes/{sceneId}")
    public StudioScene updateScene(
            @PathVariable String id,
            @PathVariable String sceneId,
            @RequestBody StudioScene scene
    ) {
        String tenant = TenantContext.get();
        if (scene != null && scene.id() != null && !sceneId.equals(scene.id())) {
            throw new IllegalArgumentException("Path sceneId does not match body id: " + sceneId + " vs " + scene.id());
        }
        return studioService.updateScene(tenant, id, scene);
    }

    /**
     * 边界关键帧法：计算并绑定 N+1 边界关键帧
     * POST /api/v1/creative/studio/projects/{id}/keyframes/prepare
     */
    @PostMapping("/projects/{id}/keyframes/prepare")
    public List<StudioScene> prepareBoundaryKeyframes(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.prepareBoundaryKeyframes(tenant, id);
    }

    /**
     * 批量派发生成各分镜视频切片
     * POST /api/v1/creative/studio/projects/{id}/scenes/generate-all
     */
    @PostMapping("/projects/{id}/scenes/generate-all")
    public List<StudioScene> generateSceneClips(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.generateSceneClips(tenant, id);
    }

    /**
     * 导出剪映 (CapCut) 工程草稿包与合成
     * POST /api/v1/creative/studio/projects/{id}/export-capcut
     */
    @PostMapping("/projects/{id}/export-capcut")
    public CapCutDraftExportResult exportCapCutDraft(@PathVariable String id) {
        String tenant = TenantContext.get();
        return studioService.exportCapCutDraft(tenant, id);
    }

    /**
     * 驱动数字人主播生成唇形对齐视频
     * POST /api/v1/creative/studio/digital-human/drive
     */
    @PostMapping("/digital-human/drive")
    public DigitalHumanDriveResult driveDigitalHuman(@Valid @RequestBody DigitalHumanDriveRequest request) {
        String tenant = TenantContext.get();
        return digitalHumanService.driveAvatar(tenant, request.avatarId(), request.voiceoverText(), request.speaker());
    }

    /**
     * 获取可用数字人形象列表
     * GET /api/v1/creative/studio/digital-human/avatars
     */
    @GetMapping("/digital-human/avatars")
    public List<DigitalHumanAvatar> listDigitalHumanAvatars() {
        String tenant = TenantContext.get();
        return digitalHumanService.listAvatars(tenant);
    }

    /**
     * 注册/创建自定义数字人形象
     * POST /api/v1/creative/studio/digital-human/avatars
     */
    @PostMapping("/digital-human/avatars")
    @ResponseStatus(HttpStatus.CREATED)
    public DigitalHumanAvatar registerAvatar(@Valid @RequestBody com.affiliate.platform.web.studio.dto.AvatarRegisterRequest request) {
        String tenant = TenantContext.get();
        return digitalHumanService.registerAvatar(tenant, request.avatarName(), request.originalImageUrl(), request.maskImageUrl(), request.speakerCode());
    }

    /**
     * 查询指定数字人形象详情
     * GET /api/v1/creative/studio/digital-human/avatars/{avatarId}
     */
    @GetMapping("/digital-human/avatars/{avatarId}")
    public DigitalHumanAvatar getAvatar(@PathVariable String avatarId) {
        String tenant = TenantContext.get();
        return digitalHumanService.getAvatar(tenant, avatarId)
                .orElseThrow(() -> new IllegalArgumentException("Digital human avatar not found: " + avatarId));
    }

    /**
     * 删除指定数字人形象
     * DELETE /api/v1/creative/studio/digital-human/avatars/{avatarId}
     */
    @DeleteMapping("/digital-human/avatars/{avatarId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar(@PathVariable String avatarId) {
        String tenant = TenantContext.get();
        boolean deleted = digitalHumanService.deleteAvatar(tenant, avatarId);
        if (!deleted) {
            throw new IllegalArgumentException("Avatar not found or unable to delete: " + avatarId);
        }
    }
}
