package com.affiliate.platform.ai.model;

/**
 * 接入支持的大语言模型服务提供商 (AI Model Provider)
 */
public enum AiProvider {

    OPENAI("https://api.openai.com/v1", "gpt-4o", "Bearer"),
    DEEPSEEK("https://api.deepseek.com/v1", "deepseek-chat", "Bearer"),
    CLAUDE("https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022", "x-api-key"),
    QWEN("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "Bearer"),
    OLLAMA("http://localhost:11434/v1", "llama3", "Bearer"),
    CUSTOM("http://localhost:8000/v1", "custom-model", "Bearer");

    private final String defaultBaseUrl;
    private final String defaultModel;
    private final String authScheme;

    AiProvider(String defaultBaseUrl, String defaultModel, String authScheme) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.authScheme = authScheme;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public String getAuthScheme() {
        return authScheme;
    }
}
