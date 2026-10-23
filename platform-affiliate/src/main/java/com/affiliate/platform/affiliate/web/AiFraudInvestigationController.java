package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.service.AiFraudInvestigationService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 反作弊 AI 异常取证与调查 REST 控制器 (AI Anti-Fraud Investigation Controller)
 */
@RestController
@RequestMapping({"/affiliate/anti-fraud", "/api/v1/affiliate/anti-fraud"})
public class AiFraudInvestigationController {

    private final AiFraudInvestigationService investigationService;

    public AiFraudInvestigationController(AiFraudInvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @PostMapping("/ai-investigate")
    public ResponseEntity<AiFraudInvestigationService.FraudInvestigationReport> investigate(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestBody InvestigateRequest request
    ) {
        String tenantId = (tenantHeader != null && !tenantHeader.isBlank())
                ? tenantHeader
                : (request.tenantId() != null ? request.tenantId() : TenantContext.get());

        var report = investigationService.investigateAnomaly(
                tenantId,
                request.clickId(),
                request.anomalyType(),
                request.telemetry()
        );

        return ResponseEntity.ok(report);
    }

    public record InvestigateRequest(
            String tenantId,
            String clickId,
            String anomalyType,
            Map<String, Object> telemetry
    ) {}
}
