package com.affiliate.platform.ai.web;

import com.affiliate.platform.ai.model.AiChatRequest;
import com.affiliate.platform.ai.model.AiChatResponse;
import com.affiliate.platform.ai.service.AiClient;
import com.affiliate.platform.ai.service.AiModelConfigService;
import com.affiliate.platform.ai.service.AiUsageMeteringService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 统一 AI 交互网关与配额统计端点 (AI Gateway REST Controller)
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiGatewayController {

    private final AiClient aiClient;
    private final AiModelConfigService configService;
    private final AiUsageMeteringService meteringService;

    public AiGatewayController(
            AiClient aiClient,
            AiModelConfigService configService,
            AiUsageMeteringService meteringService
    ) {
        this.aiClient = aiClient;
        this.configService = configService;
        this.meteringService = meteringService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        String tenantId = request.tenantId() != null ? request.tenantId() : TenantContext.get();
        var config = configService.resolveActiveModel(tenantId);

        AiChatRequest normalized = new AiChatRequest(
                tenantId,
                request.callerModule() != null ? request.callerModule() : "DIRECT_API",
                request.messages(),
                request.modelOverride() != null ? request.modelOverride() : config.modelName(),
                request.temperature() != null ? request.temperature() : config.temperature(),
                request.maxTokens() != null ? request.maxTokens() : config.maxTokens()
        );

        AiChatResponse response = aiClient.chat(normalized, config);
        meteringService.recordUsage(
                tenantId,
                normalized.callerModule(),
                response.modelName(),
                response.usage(),
                response.latencyMs(),
                response.fallbackUsed(),
                null
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<AiUsageMeteringService.TenantAiStats> getStats() {
        return ResponseEntity.ok(meteringService.getTenantStats(TenantContext.get()));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AiUsageMeteringService.AiLogEntry>> getLogs(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(meteringService.getRecentLogs(TenantContext.get(), limit));
    }
}
