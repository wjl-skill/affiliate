package com.affiliate.platform.service.studio.scene;

import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.domain.studio.StudioScene;

import java.util.List;

/**
 * 边界关键帧法服务：为 N 个分镜全并行生成 N+1 张衔接关键帧并绑定
 */
public interface BoundaryKeyframeService {

    /**
     * 为分镜列表生成 N+1 张边界关键帧并关联至各分镜起止属性中
     */
    List<StudioScene> bindBoundaryKeyframes(StudioProject project, List<StudioScene> scenes);
}
