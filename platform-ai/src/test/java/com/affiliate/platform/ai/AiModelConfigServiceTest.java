package com.affiliate.platform.ai;

import com.affiliate.platform.ai.model.AiModelConfig;
import com.affiliate.platform.ai.model.AiProvider;
import com.affiliate.platform.ai.service.AiClient;
import com.affiliate.platform.ai.service.AiModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AiModelConfigServiceTest {

    private AiModelConfigService service;
    private AiClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(AiClient.class);
        service = new AiModelConfigService(mockClient);
    }

    @Test
    void testInitDefaultsAndListing() {
        List<AiModelConfig> list = service.listConfigs("default", true);
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(c -> c.provider() == AiProvider.DEEPSEEK));
        assertTrue(list.stream().anyMatch(c -> c.provider() == AiProvider.OPENAI));

        // 验证脱敏
        for (AiModelConfig c : list) {
            if (c.apiKey() != null && !c.apiKey().isEmpty()) {
                assertTrue(c.apiKey().contains("***") || c.apiKey().equals("******"));
            }
        }
    }

    @Test
    void testSaveAndTenantIsolation() {
        AiModelConfig custom = new AiModelConfig(
                null,
                "tenant_vip_1",
                "Tenant DeepSeek",
                AiProvider.DEEPSEEK,
                "deepseek-chat",
                "sk-custom-secret-key-123456",
                "https://api.deepseek.com/v1",
                java.math.BigDecimal.valueOf(0.5),
                1024,
                java.math.BigDecimal.ONE,
                15,
                true,
                true,
                1,
                null,
                null
        );

        AiModelConfig saved = service.saveOrUpdate(custom);
        assertNotNull(saved.id());
        assertEquals("tenant_vip_1", saved.tenantId());

        // 查询脱敏结果
        Optional<AiModelConfig> queried = service.getById(saved.id(), true);
        assertTrue(queried.isPresent());
        assertTrue(queried.get().apiKey().startsWith("sk-"));
        assertTrue(queried.get().apiKey().contains("****"));

        // 测试租户默认模型解析
        AiModelConfig resolved = service.resolveActiveModel("tenant_vip_1");
        assertEquals(saved.id(), resolved.id());
        assertEquals("sk-custom-secret-key-123456", resolved.apiKey()); // 内部调用保留未脱敏密钥
    }

    @Test
    void testSetDefaultAndFailoverPriority() {
        AiModelConfig cfg1 = service.saveOrUpdate(new AiModelConfig(
                "aimc_1", "t1", "Model 1", AiProvider.OPENAI, "gpt-4o", "sk-1", null, null, null, null, null, false, true, 2, null, null
        ));
        AiModelConfig cfg2 = service.saveOrUpdate(new AiModelConfig(
                "aimc_2", "t1", "Model 2", AiProvider.DEEPSEEK, "deepseek-chat", "sk-2", null, null, null, null, null, false, true, 1, null, null
        ));

        // 优先级最低（数字最小）先被召回
        AiModelConfig resolved = service.resolveActiveModel("t1");
        assertEquals("aimc_2", resolved.id());

        // 显式将 cfg1 设置为默认
        service.setDefault("aimc_1", "t1");
        AiModelConfig resolvedDefault = service.resolveActiveModel("t1");
        assertEquals("aimc_1", resolvedDefault.id());
    }
}
