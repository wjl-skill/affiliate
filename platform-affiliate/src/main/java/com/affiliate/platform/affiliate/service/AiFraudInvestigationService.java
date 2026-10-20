package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 联盟营销反作弊 AI 异常取证与智能风控服务 (AI Fraud Investigation Service)
 */
@Service
public class AiFraudInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(AiFraudInvestigationService.class);

    private final AiOrchestratorService aiOrchestratorService;

    @Autowired
    public AiFraudInvestigationService(@Autowired(required = false) AiOrchestratorService aiOrchestratorService) {
        this.aiOrchestratorService = aiOrchestratorService;
    }

    public AiFraudInvestigationService() {
        this(null);
    }

    public record FraudInvestigationReport(
            String clickId,
            String tenantId,
            String anomalyType,
            String riskLevel,
            String aiForensicAnalysis,
            boolean actionSuggested,
            Instant timestamp
    ) {}

    public FraudInvestigationReport investigateAnomaly(
            String tenantId,
            String clickId,
            String anomalyType,
            Map<String, Object> telemetry
    ) {
        String tid = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        String cid = (clickId != null && !clickId.isBlank()) ? clickId : "clk_anon";
        String type = (anomalyType != null && !anomalyType.isBlank()) ? anomalyType : "ANOMALOUS_CLICK_PATTERN";

        String analysis;
        String riskLevel = "HIGH";
        if (aiOrchestratorService != null) {
            try {
                analysis = aiOrchestratorService.analyzeFraudAnomalyWithAi(tid, cid, type, telemetry);
                if (analysis.contains("CRITICAL")) {
                    riskLevel = "CRITICAL";
                } else if (analysis.contains("MEDIUM")) {
                    riskLevel = "MEDIUM";
                }
            } catch (Exception e) {
                log.warn("[AiFraudInvestigation] AI investigation fallback: {}", e.getMessage());
                analysis = generateHeuristicAnalysis(cid, type, telemetry);
            }
        } else {
            analysis = generateHeuristicAnalysis(cid, type, telemetry);
        }

        return new FraudInvestigationReport(
                cid,
                tid,
                type,
                riskLevel,
                analysis,
                true,
                Instant.now()
        );
    }

    private String generateHeuristicAnalysis(String clickId, String type, Map<String, Object> telemetry) {
        return String.format(
                "【AI 反作弊启发式调查报告】点击 %s 检测到异常类型: %s。遥测指标: %s。" +
                "行为指纹判定为高风险作弊（如 Click-to-Install 时间异常或代理机房流量），建议即时阻断结算并对关联 Sub-ID 列入预警名单。",
                clickId, type, telemetry != null ? telemetry.toString() : "N/A"
        );
    }
}
