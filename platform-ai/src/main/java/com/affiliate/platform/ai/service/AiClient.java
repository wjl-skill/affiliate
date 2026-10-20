package com.affiliate.platform.ai.service;

import com.affiliate.platform.ai.model.AiChatRequest;
import com.affiliate.platform.ai.model.AiChatResponse;
import com.affiliate.platform.ai.model.AiModelConfig;

/**
 * 大模型底座网关通信客户端契约 (AI Model Client Contract)
 */
public interface AiClient {

    /**
     * 向指定的大模型配置发起单次或多次对话请求
     */
    AiChatResponse chat(AiChatRequest request, AiModelConfig config);

    /**
     * 测试配置连通性与鉴权有效性
     */
    boolean testConnection(AiModelConfig config);
}
