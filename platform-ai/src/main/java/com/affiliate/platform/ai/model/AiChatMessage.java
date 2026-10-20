package com.affiliate.platform.ai.model;

/**
 * AI 对话单条消息记录
 */
public record AiChatMessage(
        String role, // system, user, assistant
        String content
) {
    public static AiChatMessage system(String content) {
        return new AiChatMessage("system", content);
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage("user", content);
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage("assistant", content);
    }
}
