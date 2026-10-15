package com.affiliate.platform.service.ai.provider;

import com.affiliate.platform.service.ai.provider.dto.ProviderQueryResult;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitRequest;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitResult;

/**
 * 供应商协议适配器顶级接口 (AI Video Provider Adapter)
 * <p>
 * 仅负责厂商特定的 HTTP 协议、鉴权、签名、异步提交与任务状态查询，不处理具体模型业务语义。
 */
public interface AiVideoProviderAdapter {

    /**
     * 该适配器支持的供应商编码 (如 "BYTEDANCE_ARK", "DASHSCOPE", "LOCAL_MOCK")
     */
    String supportProviderCode();

    /**
     * 提交异步生成任务
     */
    ProviderSubmitResult submit(ProviderSubmitRequest request);

    /**
     * 查询任务当前生成状态
     */
    ProviderQueryResult queryTask(String remoteTaskId);

    /**
     * 取消未完成的任务 (可选)
     */
    default boolean cancelTask(String remoteTaskId) {
        return false;
    }
}
