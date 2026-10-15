package com.affiliate.platform.service.ai.meta;

import com.affiliate.platform.domain.ai.AiVideoModelMetaDTO;
import com.affiliate.platform.domain.ai.VideoGenerateRequest;

import java.util.List;
import java.util.Optional;

/**
 * 视频模型元数据服务接口 (Model Meta Service)
 */
public interface AiVideoModelMetaService {

    /**
     * 获取全量已启用的模型元数据列表
     */
    List<AiVideoModelMetaDTO> listAvailableModels();

    /**
     * 根据模型编码获取元数据
     */
    Optional<AiVideoModelMetaDTO> getModel(String modelCode);

    /**
     * 校验生成请求入参是否满足模型元数据约束
     *
     * @param request 业务生成请求
     * @throws IllegalArgumentException 当参数不符合模型约束时抛出
     */
    void validateRequest(VideoGenerateRequest request);
}
