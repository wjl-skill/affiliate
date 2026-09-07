package com.affiliate.platform.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PivotAndCohortTest {

    @Test
    void pivotAggregationMultiDimensionAndSorting() {
        PivotAggregationEngine engine = new PivotAggregationEngine();

        // 模拟 4 条明细记录
        List<PivotAggregationEngine.PivotRecord> records = List.of(
                new PivotAggregationEngine.PivotRecord(
                        Map.of("offerId", "off_1", "country", "US", "device", "MOBILE"),
                        1000, 100, 10, new BigDecimal("50.00"), new BigDecimal("80.00")
                ),
                new PivotAggregationEngine.PivotRecord(
                        Map.of("offerId", "off_1", "country", "US", "device", "DESKTOP"),
                        500, 20, 2, new BigDecimal("10.00"), new BigDecimal("15.00")
                ),
                new PivotAggregationEngine.PivotRecord(
                        Map.of("offerId", "off_1", "country", "DE", "device", "MOBILE"),
                        800, 40, 4, new BigDecimal("20.00"), new BigDecimal("35.00")
                ),
                new PivotAggregationEngine.PivotRecord(
                        Map.of("offerId", "off_2", "country", "US", "device", "MOBILE"),
                        2000, 150, 15, new BigDecimal("70.00"), new BigDecimal("120.00")
                )
        );

        // 按 ["offerId", "country"] 透视聚合，按 revenue 降序
        List<PivotAggregationEngine.PivotRow> rows = engine.aggregate(
                records,
                List.of("offerId", "country"),
                null,
                "revenue",
                true
        );

        assertEquals(3, rows.size(), "应当聚合为 off_1+US, off_2+US, off_1+DE 3 个维度分组");

        // 第一行应该是 off_2 + US (revenue=120.00)
        assertEquals("off_2", rows.get(0).dimensions().get("offerId"));
        assertEquals("US", rows.get(0).dimensions().get("country"));
        assertEquals(new BigDecimal("120.00"), rows.get(0).revenue());

        // 第二行应该是 off_1 + US (revenue = 80 + 15 = 95.00)
        assertEquals("off_1", rows.get(1).dimensions().get("offerId"));
        assertEquals("US", rows.get(1).dimensions().get("country"));
        assertEquals(1500, rows.get(1).impressions());
        assertEquals(120, rows.get(1).clicks());
        assertEquals(12, rows.get(1).conversions());
        assertEquals(new BigDecimal("95.00"), rows.get(1).revenue());
        assertEquals(new BigDecimal("60.00"), rows.get(1).spend());

        // 衍生指标验证 (CTR = 120 / 1500 * 100 = 8.0%)
        assertEquals(8.0, rows.get(1).metrics().ctrPercent(), 0.001);

        // 带前置过滤：只看 country=US
        List<PivotAggregationEngine.PivotRow> filteredRows = engine.aggregate(
                records,
                List.of("offerId"),
                Map.of("country", "US"),
                "conversions",
                true
        );
        assertEquals(2, filteredRows.size());
        assertEquals("off_2", filteredRows.get(0).dimensions().get("offerId"));
        assertEquals(15, filteredRows.get(0).conversions());
    }

    @Test
    void cohortRetentionAndLtvMatrix() {
        CohortAnalysisService service = new CohortAnalysisService();
        LocalDate cohortDay = LocalDate.of(2026, 8, 1);

        // 登记 2 个用户在 2026-08-01 获客，总 CAC 成本 10.00 (单客 5.00)
        service.recordAcquisition("user_1", cohortDay, new BigDecimal("5.00"));
        service.recordAcquisition("user_2", cohortDay, new BigDecimal("5.00"));

        // D0: user_1 产生 $3.00, user_2 产生 $2.00 -> 累计 $5.00, LTV = 2.50
        service.recordActivity("user_1", cohortDay, new BigDecimal("3.00"));
        service.recordActivity("user_2", cohortDay, new BigDecimal("2.00"));

        // D1: user_1 留存活跃产生 $4.00, user_2 未活跃 -> 活跃 1/2 = 50% 留存率, 累计 $9.00, LTV = 4.50
        service.recordActivity("user_1", cohortDay.plusDays(1), new BigDecimal("4.00"));

        // D3: user_1 和 user_2 均活跃，分别产生 $2.00 和 $3.00 -> 活跃 2/2 = 100% 留存率, 累计 $14.00, LTV = 7.00
        service.recordActivity("user_1", cohortDay.plusDays(3), new BigDecimal("2.00"));
        service.recordActivity("user_2", cohortDay.plusDays(3), new BigDecimal("3.00"));

        CohortAnalysisService.CohortMatrix matrix = service.generateMatrix(cohortDay, cohortDay);
        assertEquals(1, matrix.rows().size());

        CohortAnalysisService.CohortRow row = matrix.rows().get(0);
        assertEquals(2, row.cohortSize());
        assertEquals(new BigDecimal("10.00"), row.acquisitionCost());
        assertEquals(new BigDecimal("5.0000"), row.cac());

        // 留存率检验
        assertEquals(50.0, row.retentionRates().get(1));
        assertEquals(100.0, row.retentionRates().get(3));

        // LTV 检验
        assertEquals(new BigDecimal("2.5000"), row.cumulativeLtv().get(0));
        assertEquals(new BigDecimal("4.5000"), row.cumulativeLtv().get(1));
        assertEquals(new BigDecimal("7.0000"), row.cumulativeLtv().get(3));

        // 回本天数检验：CAC=5.00, D3 LTV=7.00 >= 5.00 -> 回本发生在 D3
        assertEquals(3, row.paybackDay());
    }
}
