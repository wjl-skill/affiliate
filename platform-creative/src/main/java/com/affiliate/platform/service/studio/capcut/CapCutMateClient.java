package com.affiliate.platform.service.studio.capcut;

import com.affiliate.platform.domain.studio.CapCutDraftExportResult;
import com.affiliate.platform.domain.studio.CapCutTrackSegment;
import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.domain.studio.StudioScene;

import java.util.List;

/**
 * 剪映/CapCut 工程草稿客户端：执行微秒级多轨道装配与草稿工程包生成
 */
public interface CapCutMateClient {

    /**
     * 将项目与分镜列表组装并导出剪映草稿工程
     */
    CapCutDraftExportResult exportDraft(StudioProject project, List<StudioScene> scenes);

    /**
     * 构建 4 条标准轨道的微秒对齐时间轴片段列表
     */
    List<CapCutTrackSegment> buildTimelineTracks(StudioProject project, List<StudioScene> scenes);

    /**
     * 序列化生成标准 draft_content.json 文本
     */
    String generateDraftJson(StudioProject project, List<StudioScene> scenes);
}
