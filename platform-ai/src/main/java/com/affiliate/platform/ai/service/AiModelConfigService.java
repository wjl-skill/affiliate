package com.affiliate.platform.ai.service;

import com.affiliate.platform.ai.model.AiModelConfig;
import com.affiliate.platform.ai.model.AiProvider;
import com.affiliate.platform.entity.AiModelConfigEntity;
import com.affiliate.platform.mapper.AiModelConfigMapper;
import com.affiliate.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 租户级 AI 大模型配置管理服务 (AI Model Configuration Service)
 */
@Service
public class AiModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfigService.class);

    // 内存安全存储（支持多租户高并发访问）
    private final Map<String, AiModelConfig> store = new ConcurrentHashMap<>();
    private final AiClient aiClient;
    private final AiModelConfigMapper modelConfigMapper;

    @Autowired
    public AiModelConfigService(
            AiClient aiClient,
            @Autowired(required = false) AiModelConfigMapper modelConfigMapper
    ) {
        this.aiClient = aiClient;
        this.modelConfigMapper = modelConfigMapper;
        initSystemDefaults();
        preloadFromDatabase();
    }

    public AiModelConfigService(AiClient aiClient) {
        this(aiClient, null);
    }

    private void preloadFromDatabase() {
        if (modelConfigMapper != null) {
            try {
                List<AiModelConfigEntity> list = modelConfigMapper.selectList(null);
                if (list != null && !list.isEmpty()) {
                    for (AiModelConfigEntity e : list) {
                        try {
                            AiProvider prov = AiProvider.valueOf(e.getProvider());
                            store.put(e.getId(), new AiModelConfig(
                                    e.getId(), e.getTenantId(), e.getName(), prov, e.getModelName(),
                                    e.getApiKeyEncrypted(), e.getBaseUrl(), e.getTemperature(), e.getMaxTokens(),
                                    e.getTopP(), e.getTimeoutSeconds(), e.getIsDefault(), e.getActive(),
                                    e.getPriority(), e.getCreatedAt(), e.getUpdatedAt()
                            ));
                        } catch (Exception ex) {
                            log.debug("Skip invalid DB model record: {}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[AiModelConfigService] Database preload skipped: {}", e.getMessage());
            }
        }
    }

    private void initSystemDefaults() {
        // 预设全局可用的 DeepSeek 与 OpenAI 种子配置
        AiModelConfig deepseekDefault = new AiModelConfig(
                "aimc_global_deepseek",
                "default",
                "DeepSeek Global Production",
                AiProvider.DEEPSEEK,
                "deepseek-chat",
                "",
                AiProvider.DEEPSEEK.getDefaultBaseUrl(),
                java.math.BigDecimal.valueOf(0.70),
                2048,
                java.math.BigDecimal.valueOf(1.00),
                30,
                true,
                true,
                1,
                Instant.now(),
                Instant.now()
        );
        store.put(deepseekDefault.id(), deepseekDefault);

        AiModelConfig openAiDefault = new AiModelConfig(
                "aimc_global_openai",
                "default",
                "OpenAI GPT-4o Production",
                AiProvider.OPENAI,
                "gpt-4o",
                "",
                AiProvider.OPENAI.getDefaultBaseUrl(),
                java.math.BigDecimal.valueOf(0.70),
                2048,
                java.math.BigDecimal.valueOf(1.00),
                30,
                false,
                true,
                2,
                Instant.now(),
                Instant.now()
        );
        store.put(openAiDefault.id(), openAiDefault);
    }

    public List<AiModelConfig> listConfigs(String tenantId, boolean maskKeys) {
        String targetTenant = tenantId != null ? tenantId : TenantContext.get();
        if (targetTenant == null || targetTenant.isBlank()) {
            targetTenant = "default";
        }

        final String finalTenant = targetTenant;
        List<AiModelConfig> list = store.values().stream()
                .filter(c -> finalTenant.equals(c.tenantId()) || "default".equals(c.tenantId()))
                .sorted(Comparator.comparing(AiModelConfig::priority, Comparator.nullsLast(Integer::compareTo)))
                .map(c -> maskKeys ? c.maskApiKey() : c)
                .toList();

        return list;
    }

    public Optional<AiModelConfig> getById(String id, boolean maskKeys) {
        AiModelConfig cfg = store.get(id);
        if (cfg == null) {
            return Optional.empty();
        }
        return Optional.of(maskKeys ? cfg.maskApiKey() : cfg);
    }

    public AiModelConfig saveOrUpdate(AiModelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AI model config cannot be null");
        }
        String tenantId = config.tenantId() != null && !config.tenantId().isBlank()
                ? config.tenantId()
                : (TenantContext.get() != null ? TenantContext.get() : "default");

        String id = config.id() != null && !config.id().isBlank()
                ? config.id()
                : "aimc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 如果更新时传入了脱敏密钥，则保留原有真实密钥
        String apiKey = config.apiKey();
        if (apiKey != null && apiKey.contains("****") && store.containsKey(id)) {
            apiKey = store.get(id).apiKey();
        }

        // 如果设置为默认模型，则清空当前租户下其他配置的默认标志
        if (Boolean.TRUE.equals(config.isDefault())) {
            store.values().stream()
                    .filter(c -> tenantId.equals(c.tenantId()) && !c.id().equals(id))
                    .forEach(c -> store.put(c.id(), c.withDefault(false)));
        }

        AiModelConfig toSave = new AiModelConfig(
                id,
                tenantId,
                config.name() != null ? config.name() : config.provider().name() + " Model",
                config.provider() != null ? config.provider() : AiProvider.DEEPSEEK,
                config.modelName() != null ? config.modelName() : "deepseek-chat",
                apiKey,
                config.baseUrl() != null ? config.baseUrl() : (config.provider() != null ? config.provider().getDefaultBaseUrl() : "https://api.deepseek.com/v1"),
                config.temperature() != null ? config.temperature() : java.math.BigDecimal.valueOf(0.70),
                config.maxTokens() != null ? config.maxTokens() : 2048,
                config.topP() != null ? config.topP() : java.math.BigDecimal.valueOf(1.00),
                config.timeoutSeconds() != null ? config.timeoutSeconds() : 30,
                config.isDefault() != null ? config.isDefault() : false,
                config.active() != null ? config.active() : true,
                config.priority() != null ? config.priority() : 1,
                store.containsKey(id) ? store.get(id).createdAt() : Instant.now(),
                Instant.now()
        );

        store.put(id, toSave);
        if (modelConfigMapper != null) {
            try {
                AiModelConfigEntity entity = new AiModelConfigEntity(
                        toSave.id(), toSave.tenantId(), toSave.name(), toSave.provider().name(), toSave.modelName(),
                        toSave.apiKey(), toSave.baseUrl(), toSave.temperature(), toSave.maxTokens(),
                        toSave.topP(), toSave.timeoutSeconds(), toSave.isDefault(), toSave.active(),
                        toSave.priority(), toSave.createdAt(), toSave.updatedAt()
                );
                if (modelConfigMapper.selectById(toSave.id()) != null) {
                    modelConfigMapper.updateById(entity);
                } else {
                    modelConfigMapper.insert(entity);
                }
            } catch (Exception e) {
                log.debug("[AiModelConfigService] Database write skipped: {}", e.getMessage());
            }
        }
        log.info("[AiModelConfigService] Saved AI config: id={}, tenant={}, provider={}, model={}",
                toSave.id(), toSave.tenantId(), toSave.provider(), toSave.modelName());
        return toSave;
    }

    public boolean delete(String id) {
        if (id != null && store.containsKey(id)) {
            store.remove(id);
            if (modelConfigMapper != null) {
                try {
                    modelConfigMapper.deleteById(id);
                } catch (Exception e) {
                    log.debug("[AiModelConfigService] Database delete skipped: {}", e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    public boolean setDefault(String id, String tenantId) {
        AiModelConfig target = store.get(id);
        if (target == null) {
            return false;
        }
        String tid = tenantId != null ? tenantId : target.tenantId();
        store.values().stream()
                .filter(c -> tid.equals(c.tenantId()))
                .forEach(c -> store.put(c.id(), c.withDefault(c.id().equals(id))));
        return true;
    }

    public AiModelConfig resolveActiveModel(String tenantId) {
        String tid = tenantId != null ? tenantId : (TenantContext.get() != null ? TenantContext.get() : "default");

        // 1. 尝试查找当前租户自定义的默认且激活的配置
        Optional<AiModelConfig> tenantDefault = store.values().stream()
                .filter(c -> tid.equals(c.tenantId()) && Boolean.TRUE.equals(c.active()) && Boolean.TRUE.equals(c.isDefault()))
                .findFirst();
        if (tenantDefault.isPresent()) {
            return tenantDefault.get();
        }

        // 2. 尝试查找当前租户优先级最高且激活的配置
        Optional<AiModelConfig> tenantFirst = store.values().stream()
                .filter(c -> tid.equals(c.tenantId()) && Boolean.TRUE.equals(c.active()))
                .min(Comparator.comparing(AiModelConfig::priority, Comparator.nullsLast(Integer::compareTo)));
        if (tenantFirst.isPresent()) {
            return tenantFirst.get();
        }

        // 3. 兜底到全局 default 租户配置
        return store.values().stream()
                .filter(c -> "default".equals(c.tenantId()) && Boolean.TRUE.equals(c.active()))
                .min(Comparator.comparing(AiModelConfig::priority, Comparator.nullsLast(Integer::compareTo)))
                .orElseGet(() -> AiModelConfig.createDefault("default", AiProvider.DEEPSEEK, ""));
    }

    public boolean testConnection(String id) {
        AiModelConfig config = store.get(id);
        if (config == null) {
            return false;
        }
        return aiClient.testConnection(config);
    }
}
