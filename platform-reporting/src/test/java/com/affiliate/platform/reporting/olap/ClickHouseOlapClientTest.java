package com.affiliate.platform.reporting.olap;

import com.affiliate.platform.reporting.PerformanceMetricsCalculator;
import com.affiliate.platform.reporting.olap.OlapReportingEngine.OlapAggregationRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClickHouseOlapClientTest {

    private ClickHouseOlapClient client;

    @BeforeEach
    void setUp() {
        client = new ClickHouseOlapClient(
                "http://localhost:8123", "default", "default", "", false, null
        );
    }

    @Test
    void testBuildAggregationSql() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 20);

        String sql = client.buildAggregationSql("tenant_1", from, to, "sub1", 100);

        assertNotNull(sql);
        assertTrue(sql.contains("SELECT sub1 AS dim_value"));
        assertTrue(sql.contains("FROM default.affiliate_report_daily_olap"));
        assertTrue(sql.contains("WHERE tenant_id = 'tenant_1'"));
        assertTrue(sql.contains("report_date >= '2026-10-01'"));
        assertTrue(sql.contains("report_date <= '2026-10-20'"));
        assertTrue(sql.contains("GROUP BY sub1"));
        assertTrue(sql.contains("ORDER BY total_clicks DESC"));
        assertTrue(sql.contains("LIMIT 100"));
        assertTrue(sql.contains("FORMAT JSONEachRow"));
    }

    @Test
    void testParseJsonEachRow() {
        String jsonl = """
                {"dim_value":"tiktok_ugc","total_clicks":1500,"total_conversions":75,"total_payout":375.00,"total_revenue":600.00}
                {"dim_value":"fb_reels","total_clicks":1000,"total_conversions":40,"total_payout":200.00,"total_revenue":320.00}
                """;

        List<OlapAggregationRow> rows = client.parseJsonEachRow(jsonl);

        assertEquals(2, rows.size());
        OlapAggregationRow row1 = rows.get(0);
        assertEquals("tiktok_ugc", row1.dimensionKey());
        assertEquals(1500L, row1.clicks());
        assertEquals(75L, row1.conversions());
        assertEquals(0, new BigDecimal("375.00").compareTo(row1.payout()));
        assertEquals(0, new BigDecimal("600.00").compareTo(row1.revenue()));
        assertEquals(0, new BigDecimal("225.00").compareTo(row1.profit()));
        assertNotNull(row1.metrics());
        assertEquals(5.0, row1.metrics().cvrPercent(), 1e-4);
    }

    @Test
    void testOlapReportingEngineDelegatesToClickHouse() {
        ClickHouseOlapClient mockCh = new ClickHouseOlapClient(
                "http://localhost:8123", "default", "default", "", false, null
        ) {
            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        var sampleRow = new OlapAggregationRow(
                "ch_dim_val", 2500, 100, BigDecimal.valueOf(500), BigDecimal.valueOf(800),
                BigDecimal.valueOf(300), PerformanceMetricsCalculator.calculate(25000, 2500, 100, BigDecimal.valueOf(500), BigDecimal.valueOf(800))
        );
        mockCh.registerMockResult("t1", "sub1", List.of(sampleRow));

        OlapReportingEngine engine = new OlapReportingEngine(null, null, mockCh);

        List<OlapAggregationRow> result = engine.queryAggregation(
                "t1", LocalDate.now().minusDays(10), LocalDate.now(), "sub1"
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ch_dim_val", result.get(0).dimensionKey());
        assertEquals(2500L, result.get(0).clicks());
    }
}
