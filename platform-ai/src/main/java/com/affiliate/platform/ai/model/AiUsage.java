package com.affiliate.platform.ai.model;

/**
 * AI 调用 Token 消耗统计
 */
public record AiUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
    public static AiUsage of(int prompt, int completion) {
        return new AiUsage(prompt, completion, prompt + completion);
    }

    public static AiUsage zero() {
        return new AiUsage(0, 0, 0);
    }
}
