package com.affiliate.platform.ai.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 租户级 AI 大模型调用配置 (AI Model Configuration)
 */
public record AiModelConfig(
        String id,
        String tenantId,
        String name,
        AiProvider provider,
        String modelName,
        String apiKey,
        String baseUrl,
        BigDecimal temperature,
        Integer maxTokens,
        BigDecimal topP,
        Integer timeoutSeconds,
        Boolean isDefault,
        Boolean active,
        Integer priority,
        Instant createdAt,
        Instant updatedAt
) {
    public static AiModelConfig createDefault(String tenantId, AiProvider provider, String apiKey) {
        String id = "aimc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new AiModelConfig(
                id,
                tenantId,
                provider.name() + " Default Profile",
                provider,
                provider.getDefaultModel(),
                apiKey,
                provider.getDefaultBaseUrl(),
                BigDecimal.valueOf(0.70),
                2048,
                BigDecimal.valueOf(1.00),
                30,
                true,
                true,
                1,
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * 抹除敏感凭证信息用于安全查询或前端展示
     */
    public AiModelConfig maskApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return this;
        }
        String masked = apiKey.length() <= 8
                ? "******"
                : apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
        return new AiModelConfig(
                id, tenantId, name, provider, modelName, masked, baseUrl,
                temperature, maxTokens, topP, timeoutSeconds, isDefault, active, priority,
                createdAt, updatedAt
        );
    }

    public AiModelConfig withId(String newId) {
        return new AiModelConfig(
                newId, tenantId, name, provider, modelName, apiKey, baseUrl,
                temperature, maxTokens, topP, timeoutSeconds, isDefault, active, priority,
                createdAt, Instant.now()
        );
    }

    public AiModelConfig withDefault(boolean def) {
        return new AiModelConfig(
                id, tenantId, name, provider, modelName, apiKey, baseUrl,
                temperature, maxTokens, topP, timeoutSeconds, def, active, priority,
                createdAt, Instant.now()
        );
    }
}
