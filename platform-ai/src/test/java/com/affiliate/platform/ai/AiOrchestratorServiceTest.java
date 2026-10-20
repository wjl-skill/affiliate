package com.affiliate.platform.ai;

import com.affiliate.platform.ai.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiOrchestratorServiceTest {

    private AiOrchestratorService orchestrator;
    private AiUsageMeteringService meteringService;

    @BeforeEach
    void setUp() {
        StandardAiClient client = new StandardAiClient();
        AiModelConfigService configService = new AiModelConfigService(client);
        AiPromptTemplateService templateService = new AiPromptTemplateService();
        meteringService = new AiUsageMeteringService();

        orchestrator = new AiOrchestratorService(
                client,
                configService,
                templateService,
                meteringService
        );
    }

    @Test
    void testDiagnoseCreativeWithAi() {
        String result = orchestrator.diagnoseCreativeWithAi(
                "tenant_test",
                "mat_101",
                "Hook drop > 85%",
                "Gen-Z Gaming",
                "Play now and get coins"
        );
        assertNotNull(result);
        assertTrue(result.contains("Hook"));

        var stats = meteringService.getTenantStats("tenant_test");
        assertEquals(1, stats.totalRequests());
        assertTrue(stats.totalTokens() > 0);
    }

    @Test
    void testGenerateAdCreativeCopy() {
        String copy = orchestrator.generateAdCreativeCopy(
                "tenant_test",
                "Affiliate Tracker Pro",
                "Performance Marketers",
                "Realtime TDS & Fraud Shield",
                "Start 14-Day Free Trial"
        );
        assertNotNull(copy);
        assertFalse(copy.isBlank());
    }

    @Test
    void testAnalyzeFraudAnomalyWithAi() {
        String fraudReport = orchestrator.analyzeFraudAnomalyWithAi(
                "tenant_test",
                "clk_98765",
                "TTIC < 800ms and Datacenter IP 192.0.2.1",
                Map.of("ipRisk", 99, "fingerprintClash", true)
        );
        assertNotNull(fraudReport);
        assertTrue(fraudReport.contains("反作弊") || fraudReport.contains("作弊") || fraudReport.contains("Fraud"));
    }

    @Test
    void testAdviseBidOptimizationWithAi() {
        String advice = orchestrator.adviseBidOptimizationWithAi(
                "tenant_test",
                "camp_us_dsp",
                BigDecimal.valueOf(2.50),
                0.025,
                0.08,
                BigDecimal.valueOf(0.80),
                0.62
        );
        assertNotNull(advice);
        assertTrue(advice.contains("出价") || advice.contains("Bid"));
    }

    @Test
    void testSynthesizeReportInsightsWithAi() {
        String report = orchestrator.synthesizeReportInsightsWithAi(
                "tenant_test",
                "2026-10-01 to 2026-10-20",
                Map.of("impressions", 1500000, "clicks", 45000, "conversions", 2200, "revenue", 55000.0)
        );
        assertNotNull(report);
        assertTrue(report.contains("商业洞察") || report.contains("经营分析") || report.contains("Report"));
    }
}
