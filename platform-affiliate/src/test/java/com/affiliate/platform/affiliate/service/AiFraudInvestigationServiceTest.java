package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class AiFraudInvestigationServiceTest {

    @Test
    void testAiFraudInvestigationWithMockOrchestrator() {
        AiOrchestratorService mockOrchestrator = Mockito.mock(AiOrchestratorService.class);
        Mockito.when(mockOrchestrator.analyzeFraudAnomalyWithAi(any(), any(), any(), any()))
                .thenReturn("CRITICAL: Detected datacenter proxy farm in ASN 16509. Suggest immediate sub-id blacklist.");

        AiFraudInvestigationService service = new AiFraudInvestigationService(mockOrchestrator);

        var report = service.investigateAnomaly(
                "tenant_aff_01",
                "clk_suspicious_999",
                "SHORT_TTIC_AND_DATACENTER_IP",
                Map.of("ip", "198.51.100.22", "tticMs", 420)
        );

        assertNotNull(report);
        assertEquals("clk_suspicious_999", report.clickId());
        assertEquals("CRITICAL", report.riskLevel());
        assertTrue(report.aiForensicAnalysis().contains("datacenter proxy farm"));
    }

    @Test
    void testInvestigationFallbackWhenOrchestratorNull() {
        AiFraudInvestigationService service = new AiFraudInvestigationService(null);

        var report = service.investigateAnomaly(
                "tenant_aff_02",
                "clk_fb_123",
                "DUPLICATE_DEVICE_FINGERPRINT",
                Map.of("duplicateCount", 18)
        );

        assertNotNull(report);
        assertEquals("HIGH", report.riskLevel());
        assertTrue(report.aiForensicAnalysis().contains("AI 反作弊启发式调查报告"));
    }
}
