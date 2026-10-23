package com.affiliate.platform.web.agent;

import com.affiliate.platform.domain.agent.CreativeAgentAction;
import com.affiliate.platform.domain.agent.CreativeAgentDiagnosis;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.service.agent.CreativeAutonomousAgentService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 营销创意自治 Agent 控制端点 (Creative Autonomous Agent Controller)
 */
@RestController
@RequestMapping("/api/v1/creative/agent")
public class CreativeAutonomousAgentController {

    private final CreativeAutonomousAgentService agentService;

    public CreativeAutonomousAgentController(CreativeAutonomousAgentService agentService) {
        this.agentService = agentService;
    }

    private String resolveTenant(String tenantHeader, String bodyTenant) {
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return tenantHeader;
        }
        if (bodyTenant != null && !bodyTenant.isBlank()) {
            return bodyTenant;
        }
        String ctx = TenantContext.get();
        return (ctx != null && !ctx.isBlank()) ? ctx : "default";
    }

    /**
     * 触发对指定素材的自治诊断与重构流水线 (同时支持 /trigger 与 /refine 端点契约)
     */
    @PostMapping({"/trigger", "/refine"})
    public ResponseEntity<Map<String, Object>> triggerAgent(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestBody AgentTriggerRequest request
    ) {
        String tenantId = resolveTenant(tenantHeader, request.tenantId());

        MaterialQualityLevel level = request.level() != null
                ? request.level()
                : MaterialQualityLevel.RISK;

        double dropOff3s = request.dropOff3s() > 0 ? request.dropOff3s() : 0.72;
        double ctr = request.ctr() > 0 ? request.ctr() : 0.0085;
        double cvr = request.cvr() > 0 ? request.cvr() : 0.0110;
        BigDecimal roi = request.roi() != null ? request.roi() : BigDecimal.valueOf(0.68);

        String effectivePrompt = request.prompt();
        if (effectivePrompt == null || effectivePrompt.isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (request.basePrompt() != null && !request.basePrompt().isBlank()) {
                sb.append(request.basePrompt().trim());
            }
            if (request.targetAudience() != null && !request.targetAudience().isBlank()) {
                sb.append(" [Target Audience: ").append(request.targetAudience().trim()).append("]");
            }
            if (request.diagnosisReason() != null && !request.diagnosisReason().isBlank()) {
                sb.append(" [Diagnosis Hint: ").append(request.diagnosisReason().trim()).append("]");
            }
            effectivePrompt = sb.toString().trim();
        }

        CreativeAgentAction action = agentService.triggerAutonomousOptimization(
                tenantId,
                request.materialId(),
                level,
                dropOff3s,
                ctr,
                cvr,
                roi,
                effectivePrompt
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "actionId", action.actionId(),
                "materialId", action.materialId(),
                "status", action.status(),
                "newProjectId", action.newProjectId() != null ? action.newProjectId() : "",
                "spawnedStudioProjectId", action.newProjectId() != null ? action.newProjectId() : "",
                "capcutDraftPath", action.capCutDraftPath() != null ? action.capCutDraftPath() : "",
                "revisedPrompt", action.revisedPrompt() != null ? action.revisedPrompt() : "",
                "refinedOpeningHook", action.refinedOpeningHook() != null ? action.refinedOpeningHook() : "",
                "refinedCta", action.refinedCta() != null ? action.refinedCta() : ""
        ));
    }

    /**
     * 查询当前租户的自治动作列表
     */
    @GetMapping("/actions")
    public ResponseEntity<List<CreativeAgentAction>> listActions(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader
    ) {
        String tenantId = resolveTenant(tenantHeader, null);
        return ResponseEntity.ok(agentService.listActions(tenantId));
    }

    /**
     * 查询素材的历史诊断结论
     */
    @GetMapping("/diagnosis/{materialId}")
    public ResponseEntity<Map<String, Object>> getDiagnosisHistory(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @PathVariable("materialId") String materialId
    ) {
        List<CreativeAgentDiagnosis> list = agentService.getDiagnosisHistory(tenantHeader, materialId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "materialId", materialId,
                "count", list.size(),
                "history", list
        ));
    }

    /**
     * 查询自治动作执行详情
     */
    @GetMapping("/actions/{actionId}")
    public ResponseEntity<Object> getActionDetail(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @PathVariable("actionId") String actionId
    ) {
        return agentService.getAction(actionId)
                .filter(a -> tenantHeader == null || tenantHeader.isBlank() || tenantHeader.equals(a.tenantId()))
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * AI 营销文案衍生变体生成
     */
    @PostMapping("/generate-copy")
    public ResponseEntity<Map<String, Object>> generateCopy(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestBody GenerateCopyRequest req
    ) {
        String tenantId = resolveTenant(tenantHeader, req.tenantId());

        String copy = agentService.generateCopyVariants(
                tenantId,
                req.productName(),
                req.targetAudience(),
                req.sellingPoints(),
                req.callToAction()
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "tenantId", tenantId,
                "productName", req.productName() != null ? req.productName() : "",
                "generatedCopy", copy
        ));
    }

    public record GenerateCopyRequest(
            String tenantId,
            String productName,
            String targetAudience,
            String sellingPoints,
            String callToAction
    ) {}

    public record AgentTriggerRequest(
            String tenantId,
            String materialId,
            MaterialQualityLevel level,
            double dropOff3s,
            double ctr,
            double cvr,
            BigDecimal roi,
            String prompt,
            String targetAudience,
            String diagnosisReason,
            String basePrompt
    ) {
        public AgentTriggerRequest(
                String tenantId,
                String materialId,
                MaterialQualityLevel level,
                double dropOff3s,
                double ctr,
                double cvr,
                BigDecimal roi,
                String prompt
        ) {
            this(tenantId, materialId, level, dropOff3s, ctr, cvr, roi, prompt, null, null, null);
        }
    }
}
