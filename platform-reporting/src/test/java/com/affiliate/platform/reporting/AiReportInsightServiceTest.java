package com.affiliate.platform.reporting;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class AiReportInsightServiceTest {

    @Test
    void testExecutiveBriefingGenerationWithAi() {
        ReportService mockReportService = Mockito.mock(ReportService.class);
        AiOrchestratorService mockOrchestrator = Mockito.mock(AiOrchestratorService.class);

        LocalDate now = LocalDate.now();
        List<ReportService.DailyReport> sampleReports = List.of(
                new ReportService.DailyReport("t_vip", now.minusDays(1), "camp_1", 10000, 500, 50, BigDecimal.valueOf(150.0), BigDecimal.valueOf(500.0)),
                new ReportService.DailyReport("t_vip", now, "camp_2", 20000, 800, 80, BigDecimal.valueOf(250.0), BigDecimal.valueOf(800.0))
        );

        Mockito.when(mockReportService.list(any(), any(), any())).thenReturn(sampleReports);
        Mockito.when(mockOrchestrator.synthesizeReportInsightsWithAi(any(), any(), any()))
                .thenReturn("AI Summary: Strong growth in offer camp_2. ROAS reached 3.25. Recommend increasing budget by 20%.");

        AiReportInsightService service = new AiReportInsightService(mockReportService, mockOrchestrator);
        var report = service.generateExecutiveInsights("t_vip", now.minusDays(2), now);

        assertNotNull(report);
        assertEquals("t_vip", report.tenantId());
        assertEquals(30000, report.totalImpressions());
        assertEquals(1300, report.totalClicks());
        assertEquals(130, report.totalConversions());
        assertEquals(BigDecimal.valueOf(400.0), report.totalSpend());
        assertEquals(BigDecimal.valueOf(1300.0), report.totalRevenue());
        assertEquals(BigDecimal.valueOf(900.0), report.netProfit());
        assertEquals(3.25, report.overallRoas(), 0.01);
        assertTrue(report.aiExecutiveBriefing().contains("Strong growth in offer camp_2"));
    }

    @Test
    void testExecutiveBriefingFallbackWhenOrchestratorNull() {
        ReportService mockReportService = Mockito.mock(ReportService.class);
        Mockito.when(mockReportService.list(any(), any(), any())).thenReturn(List.of());

        AiReportInsightService service = new AiReportInsightService(mockReportService, null);
        var report = service.generateExecutiveInsights("t_default", null, null);

        assertNotNull(report);
        assertEquals(0, report.totalImpressions());
        assertTrue(report.aiExecutiveBriefing().contains("AI 自动化经营分析与商业洞察摘要"));
    }
}
