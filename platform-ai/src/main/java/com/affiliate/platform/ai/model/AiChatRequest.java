package com.affiliate.platform.ai.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 统一多模型请求对象 (Normalized AI Chat Request)
 */
public record AiChatRequest(
        String tenantId,
        String callerModule,
        List<AiChatMessage> messages,
        String modelOverride,
        BigDecimal temperature,
        Integer maxTokens
) {
    public static AiChatRequest prompt(String tenantId, String callerModule, String systemPrompt, String userPrompt) {
        return new AiChatRequest(
                tenantId,
                callerModule,
                List.of(
                        AiChatMessage.system(systemPrompt != null ? systemPrompt : "You are an intelligent ad-tech marketing AI agent."),
                        AiChatMessage.user(userPrompt)
                ),
                null,
                null,
                null
        );
    }
}
