package com.affiliate.platform.ai.web;

import com.affiliate.platform.ai.model.AiModelConfig;
import com.affiliate.platform.ai.service.AiModelConfigService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 租户级 AI 大模型参数配置端点 (AI Model Config REST Controller)
 */
@RestController
@RequestMapping("/api/v1/ai/configs")
public class AiModelConfigController {

    private final AiModelConfigService configService;

    public AiModelConfigController(AiModelConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public List<AiModelConfig> list() {
        return configService.listConfigs(TenantContext.get(), true);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiModelConfig> get(@PathVariable String id) {
        return configService.getById(id, true)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public AiModelConfig save(@RequestBody AiModelConfig request) {
        return configService.saveOrUpdate(request).maskApiKey();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean removed = configService.delete(id);
        return ResponseEntity.ok(Map.of("deleted", removed, "id", id));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable String id) {
        boolean ok = configService.testConnection(id);
        return ResponseEntity.ok(Map.of("success", ok, "id", id));
    }

    @PutMapping("/{id}/set-default")
    public ResponseEntity<Map<String, Object>> setDefault(@PathVariable String id) {
        boolean ok = configService.setDefault(id, TenantContext.get());
        return ResponseEntity.ok(Map.of("success", ok, "defaultModelId", id));
    }
}
