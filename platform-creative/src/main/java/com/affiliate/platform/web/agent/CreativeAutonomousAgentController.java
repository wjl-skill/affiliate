package com.affiliate.platform.web.agent;

import com.affiliate.platform.domain.agent.CreativeAgentAction;
import com.affiliate.platform.domain.agent.CreativeAgentDiagnosis;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.service.agent.CreativeAutonomousAgentService;
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

    /**
     * 触发对指定素材的自治诊断与重构流水线
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerAgent(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestBody AgentTriggerRequest request
    ) {
        String tenantId = (tenantHeader != null && !tenantHeader.isBlank())
                ? tenantHeader
                : (request.tenantId() != null ? request.tenantId() : "default");

        MaterialQualityLevel level = request.level() != null
                ? request.level()
                : MaterialQualityLevel.RISK;

        CreativeAgentAction action = agentService.triggerAutonomousOptimization(
                tenantId,
                request.materialId(),
                level,
                request.dropOff3s(),
                request.ctr(),
                request.cvr(),
                request.roi() != null ? request.roi() : BigDecimal.ZERO,
                request.prompt()
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "actionId", action.actionId(),
                "status", action.status(),
                "newProjectId", action.newProjectId() != null ? action.newProjectId() : "",
                "capcutDraftPath", action.capCutDraftPath() != null ? action.capCutDraftPath() : "",
                "executionLog", action.executionLog()
        ));
    }

    /**
     * 查询素材的历史诊断结论
     */
    @GetMapping("/diagnosis/{materialId}")
    public ResponseEntity<Map<String, Object>> getDiagnosisHistory(
            @PathVariable("materialId") String materialId
    ) {
        List<CreativeAgentDiagnosis> list = agentService.getDiagnosisHistory(materialId);
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
            @PathVariable("actionId") String actionId
    ) {
        return agentService.getAction(actionId)
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
        String tenantId = (tenantHeader != null && !tenantHeader.isBlank())
                ? tenantHeader
                : (req.tenantId() != null ? req.tenantId() : "default");

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
            String prompt
    ) {}
}
