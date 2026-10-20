package com.affiliate.platform.reporting.olap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OlapReportingEngineTest {

    private OlapReportingEngine olapEngine;

    @BeforeEach
    void setUp() {
        olapEngine = new OlapReportingEngine();
    }

    @Test
    void testQueryRoutingDecision() {
        LocalDate today = LocalDate.now();

        // 1. 小范围单日查询 -> 走 PG 主库
        OlapReportingEngine.RoutingDecision d1 = olapEngine.routeQuery(
                today, today, List.of("offerId")
        );
        assertEquals(OlapReportingEngine.RoutingDecision.ROUTED_TO_TRANSACTIONAL_PG, d1);

        // 2. 长周期跨度 (如 14 天) -> 自动路由至 OLAP 加速层
        OlapReportingEngine.RoutingDecision d2 = olapEngine.routeQuery(
                today.minusDays(14), today, List.of("offerId")
        );
        assertEquals(OlapReportingEngine.RoutingDecision.ROUTED_TO_OLAP_ACCELERATOR, d2);

        // 3. 高基数维度下钻 (sub1, sub2, sub3) -> 自动路由至 OLAP 加速层
        OlapReportingEngine.RoutingDecision d3 = olapEngine.routeQuery(
                today.minusDays(1), today, List.of("offerId", "sub1", "sub2", "sub3")
        );
        assertEquals(OlapReportingEngine.RoutingDecision.ROUTED_TO_OLAP_ACCELERATOR, d3);
    }

    @Test
    void testIngestAndMultiDimensionalAggregation() {
        String tenantId = "tenant_olap_01";
        LocalDate date = LocalDate.now();

        // 摄入两笔关于 offer_101 的事实数据
        olapEngine.ingestFact(
                tenantId, date, "offer_101", "aff_99", "fb", "ad01", "c1",
                100, 5, BigDecimal.valueOf(50.00), BigDecimal.valueOf(100.00)
        );
        olapEngine.ingestFact(
                tenantId, date, "offer_101", "aff_99", "google", "ad02", "c2",
                150, 10, BigDecimal.valueOf(100.00), BigDecimal.valueOf(200.00)
        );

        // 摄入一笔关于 offer_102 的事实数据
        olapEngine.ingestFact(
                tenantId, date, "offer_102", "aff_88", "tiktok", "ad03", "c3",
                80, 2, BigDecimal.valueOf(20.00), BigDecimal.valueOf(40.00)
        );

        assertEquals(3, olapEngine.totalIngestedRecords.get());

        // 按 offerId 聚合
        List<OlapReportingEngine.OlapAggregationRow> byOffer = olapEngine.queryAggregation(
                tenantId, date.minusDays(1), date.plusDays(1), "offerId"
        );

        assertEquals(2, byOffer.size());

        // 验证 offer_101 的聚合结果：clicks=250, conversions=15, payout=150, revenue=300, profit=150
        OlapReportingEngine.OlapAggregationRow row101 = byOffer.stream()
                .filter(r -> "offer_101".equals(r.dimensionKey()))
                .findFirst()
                .orElse(null);

        assertNotNull(row101);
        assertEquals(250, row101.clicks());
        assertEquals(15, row101.conversions());
        assertEquals(BigDecimal.valueOf(150.00), row101.payout());
        assertEquals(BigDecimal.valueOf(300.00), row101.revenue());
        assertEquals(BigDecimal.valueOf(150.00), row101.profit());
        assertNotNull(row101.metrics());
        assertTrue(row101.metrics().cvrPercent() > 5.0);

        // 按 sub1 维度切片聚合
        List<OlapReportingEngine.OlapAggregationRow> bySub1 = olapEngine.queryAggregation(
                tenantId, date.minusDays(1), date.plusDays(1), "sub1"
        );
        assertEquals(3, bySub1.size()); // fb, google, tiktok
    }
}
