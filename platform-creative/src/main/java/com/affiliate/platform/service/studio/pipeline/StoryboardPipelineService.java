package com.affiliate.platform.service.studio.pipeline;

import com.affiliate.platform.domain.studio.AdTypePipeline;
import com.affiliate.platform.domain.studio.StudioAsset;
import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.domain.studio.StudioScene;

import java.util.List;

/**
 * 6 套行业分镜提示词管线与资产拆解服务接口
 */
public interface StoryboardPipelineService {

    /**
     * 根据项目广告类型解析管线
     */
    AdTypePipeline resolvePipeline(String adType);

    /**
     * 智能拆解项目中的角色与核心商品资产
     */
    List<StudioAsset> analyzeAssets(StudioProject project);

    /**
     * 依据 6 大行业分镜管线生成结构化分镜列表
     */
    List<StudioScene> generateStoryboardScenes(StudioProject project);

    /**
     * 针对特定管线构建完整的 LLM 系统提示词与用户指令
     */
    String buildPipelineSystemPrompt(AdTypePipeline pipeline);
}
