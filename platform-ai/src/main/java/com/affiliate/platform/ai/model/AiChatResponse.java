package com.affiliate.platform.ai.model;

import java.time.Instant;

/**
 * 统一多模型响应对象 (Normalized AI Chat Response)
 */
public record AiChatResponse(
        String id,
        String modelName,
        AiProvider provider,
        String content,
        AiUsage usage,
        long latencyMs,
        boolean fallbackUsed,
        String finishReason,
        Instant timestamp
) {
    public static AiChatResponse success(
            String id,
            String modelName,
            AiProvider provider,
            String content,
            AiUsage usage,
            long latencyMs
    ) {
        return new AiChatResponse(
                id, modelName, provider, content, usage, latencyMs, false, "stop", Instant.now()
        );
    }

    public static AiChatResponse fallback(
            String modelName,
            String content,
            long latencyMs
    ) {
        return new AiChatResponse(
                "resp_fb_" + System.currentTimeMillis(),
                modelName != null ? modelName : "heuristic-fallback",
                AiProvider.CUSTOM,
                content,
                AiUsage.of(50, 100),
                latencyMs,
                true,
                "fallback",
                Instant.now()
        );
    }
}
