package com.affiliate.platform.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    @Test
    void aggregatesDailyReportAndCalculatesDerivedMetrics() {
        ReportService service = new ReportService();
        LocalDate today = LocalDate.now();

        // 第 1 笔：1000 imp, 50 clicks, 5 conv, spend 20.00, revenue 30.00
        service.record("tenant1", today, "camp1", 1000, 50, 5, new BigDecimal("20.00"), new BigDecimal("30.00"));

        // 第 2 笔：1000 imp, 50 clicks, 5 conv, spend 20.00, revenue 30.00
        ReportService.DailyReport report = service.record("tenant1", today, "camp1", 1000, 50, 5, new BigDecimal("20.00"), new BigDecimal("30.00"));

        // 累计：2000 imp, 100 clicks, 10 conv, spend 40.00, revenue 60.00
        assertEquals(2000, report.impressions());
        assertEquals(100, report.clicks());
        assertEquals(10, report.conversions());
        assertEquals(new BigDecimal("40.00"), report.spend());
        assertEquals(new BigDecimal("60.00"), report.revenue());

        // 计算衍生效果指标
        DerivedMetrics metrics = service.getMetrics(report);

        // CTR % = 100 / 2000 * 100 = 5.0%
        assertEquals(5.0, metrics.ctrPercent(), 0.001);

        // CVR % = 10 / 100 * 100 = 10.0%
        assertEquals(10.0, metrics.cvrPercent(), 0.001);

        // eCPM = (40.00 / 2000) * 1000 = 20.0000
        assertEquals(0, new BigDecimal("20.0000").compareTo(metrics.ecpm()));

        // eCPC = 40.00 / 100 = 0.4000
        assertEquals(0, new BigDecimal("0.4000").compareTo(metrics.ecpc()));

        // CPA = 40.00 / 10 = 4.0000
        assertEquals(0, new BigDecimal("4.0000").compareTo(metrics.cpa()));

        // 毛利 = 40.00 - 60.00 = -20.00
        assertEquals(0, new BigDecimal("-20.0000").compareTo(metrics.grossProfit()));

        List<ReportService.DailyReport> list = service.list("tenant1", today.minusDays(1), today.plusDays(1));
        assertEquals(1, list.size());
    }
}
