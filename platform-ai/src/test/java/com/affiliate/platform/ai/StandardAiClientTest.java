package com.affiliate.platform.ai;

import com.affiliate.platform.ai.model.*;
import com.affiliate.platform.ai.service.StandardAiClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandardAiClientTest {

    @Test
    void testResilientSemanticFallbackWhenNoApiKey() {
        StandardAiClient client = new StandardAiClient();

        AiModelConfig configWithoutKey = AiModelConfig.createDefault("default", AiProvider.DEEPSEEK, "");
        AiChatRequest request = new AiChatRequest(
                "default",
                "CREATIVE",
                List.of(
                        AiChatMessage.system("You are a creative director."),
                        AiChatMessage.user("Please analyze hook script drop for material mat_01")
                ),
                null, null, null
        );

        AiChatResponse response = client.chat(request, configWithoutKey);
        assertNotNull(response);
        assertTrue(response.fallbackUsed());
        assertNotNull(response.content());
        assertTrue(response.content().contains("AI 广告创意自适应诊断与重构方案"));
        assertTrue(response.content().contains("Hook 脚本"));
        assertTrue(response.usage().totalTokens() > 0);
    }

    @Test
    void testDspFallbackReasoning() {
        StandardAiClient client = new StandardAiClient();
        AiModelConfig config = AiModelConfig.createDefault("default", AiProvider.OPENAI, "");
        AiChatRequest request = new AiChatRequest(
                "default",
                "DSP",
                List.of(AiChatMessage.user("Suggest optimal bid and bid shading")),
                null, null, null
        );

        AiChatResponse response = client.chat(request, config);
        assertNotNull(response);
        assertTrue(response.content().contains("DSP 智能出价与策略优化建议"));
        assertTrue(response.content().contains("Bid Shading"));
    }

    @Test
    void testFraudFallbackReasoning() {
        StandardAiClient client = new StandardAiClient();
        AiModelConfig config = AiModelConfig.createDefault("default", AiProvider.CLAUDE, "");
        AiChatRequest request = new AiChatRequest(
                "default",
                "AFFILIATE_FRAUD",
                List.of(AiChatMessage.user("Analyze click fraud and fake attribution")),
                null, null, null
        );

        AiChatResponse response = client.chat(request, config);
        assertNotNull(response);
        assertTrue(response.content().contains("反作弊异常归因与处置建议"));
        assertTrue(response.content().contains("Click Flooding"));
    }
}
