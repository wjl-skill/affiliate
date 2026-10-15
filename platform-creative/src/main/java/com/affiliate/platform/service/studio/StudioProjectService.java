package com.affiliate.platform.service.studio;

import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.web.studio.dto.StudioProjectCreateRequest;

import java.util.List;
import java.util.Optional;

/**
 * AI 长视频工作台核心业务应用服务接口
 */
public interface StudioProjectService {

    /**
     * ① 创建长视频新项目
     */
    StudioProject createProject(String tenantId, StudioProjectCreateRequest request);

    /**
     * 查询项目详情
     */
    Optional<StudioProject> getProject(String tenantId, String id);

    /**
     * 查询近期项目列表
     */
    List<StudioProject> listProjects(String tenantId, int limit);

    /**
     * 删除长视频项目
     */
    boolean deleteProject(String tenantId, String projectId);

    /**
     * ② 智能拆解并生成角色/核心商品资产
     */
    List<StudioAsset> analyzeAndSaveAssets(String tenantId, String projectId);

    /**
     * 查询项目名下的资产列表
     */
    List<StudioAsset> listAssets(String tenantId, String projectId);

    /**
     * ④ 基于 6 套行业管线规划分镜脚本
     */
    List<StudioScene> generateStoryboards(String tenantId, String projectId);

    /**
     * 查询项目分镜列表
     */
    List<StudioScene> listScenes(String tenantId, String projectId);

    /**
     * 更新单个分镜镜头配置
     */
    StudioScene updateScene(String tenantId, String projectId, StudioScene scene);

    /**
     * ⑤ 边界关键帧法：全并行预计算并绑定起止关键帧
     */
    List<StudioScene> prepareBoundaryKeyframes(String tenantId, String projectId);

    /**
     * ⑥ 批量派发生成各分镜视频切片
     */
    List<StudioScene> generateSceneClips(String tenantId, String projectId);

    /**
     * ⑦ 导出剪映 (CapCut) 工程草稿包与合成
     */
    CapCutDraftExportResult exportCapCutDraft(String tenantId, String projectId);
}
