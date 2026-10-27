package com.affiliate.platform.integration;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TikTok Pangle 媒体与流量变现网络连接器单元测试
 */
class TikTokPangleConnectorTest {

    @Test
    @DisplayName("测试 Pangle 平台连接器正常同步应用广告位与投放单元")
    void testPangleSyncSuccess() throws Exception {
        TikTokPangleConnector connector = new TikTokPangleConnector();

        assertEquals("PANGLE", connector.provider());
        assertEquals(TikTokPangleConnector.CircuitStatus.HEALTHY, connector.getCircuitStatus());

        PartnerConnection connection = new PartnerConnection(
                "conn-pangle-01",
                "TikTok Pangle Publisher App",
                SupplyType.SSP,
                "https://api-access.pangleglobal.com",
                Map.of("appId", "pg_app_12345", "apiToken", "mock_security_key"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        CompletableFuture<AdPlatformConnector.SyncResult> future = connector.sync(connection);
        AdPlatformConnector.SyncResult result = future.get();

        assertNotNull(result);
        assertEquals("PANGLE", result.provider());
        assertTrue(result.imported() > 0, "Pangle sync should import ad slots and placements");
        assertEquals(0, result.rejected());
        assertEquals(TikTokPangleConnector.CircuitStatus.HEALTHY, connector.getCircuitStatus());
    }

    @Test
    @DisplayName("测试 Pangle 连接器连续失败进入降级并在达到阈值后熔断拦截请求")
    void testPangleCircuitBreakerTripping() throws Exception {
        TikTokPangleConnector connector = new TikTokPangleConnector();

        PartnerConnection errorConnection = new PartnerConnection(
                "conn-pangle-err",
                "TikTok Pangle Publisher Err",
                SupplyType.SSP,
                "https://api-access.pangleglobal.com",
                Map.of("appId", "pg_app_err", "apiToken", "force_error"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        // 连续 3 次失败，进入 DEGRADED
        for (int i = 0; i < 3; i++) {
            connector.sync(errorConnection).get();
        }
        assertEquals(TikTokPangleConnector.CircuitStatus.DEGRADED, connector.getCircuitStatus());

        // 累计满 5 次失败，断路器熔断进入 CIRCUIT_OPEN
        for (int i = 0; i < 2; i++) {
            connector.sync(errorConnection).get();
        }
        assertEquals(TikTokPangleConnector.CircuitStatus.CIRCUIT_OPEN, connector.getCircuitStatus());

        // 验证熔断状态下请求被拦截
        AdPlatformConnector.SyncResult rejected = connector.sync(errorConnection).get();
        assertTrue(rejected.message().contains("Circuit breaker is OPEN"));
    }

    @Test
    @DisplayName("测试 Pangle 连接器在缺少认证凭据时明确拒绝报错")
    void testPangleMissingCredentialsFails() throws Exception {
        TikTokPangleConnector connector = new TikTokPangleConnector();

        PartnerConnection emptyConnection = new PartnerConnection(
                "conn-pangle-no-cred",
                "TikTok Pangle Publisher Empty",
                SupplyType.SSP,
                "https://api-access.pangleglobal.com",
                Map.of(),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        AdPlatformConnector.SyncResult result = connector.sync(emptyConnection).get();
        assertNotNull(result);
        assertEquals(0, result.imported());
        assertEquals(1, result.rejected());
        assertTrue(result.message().contains("Missing required Pangle authentication credentials"));
    }

    @Test
    @DisplayName("测试 Pangle 媒体端投放指标与变现报表检索")
    void testPanglePerformanceQuery() throws Exception {
        TikTokPangleConnector connector = new TikTokPangleConnector();
        AdPerformanceQuery query = new AdPerformanceQuery("pangle_app_999", "mock_token", "2026-10-27");

        AdPerformanceReport report = connector.queryPerformance(query).get();
        assertNotNull(report);
        assertEquals("PANGLE", report.platform());
        assertEquals("pangle_app_999", report.accountId());
        assertFalse(report.records().isEmpty());
        assertTrue(report.totalImpressions() > 0);
        assertTrue(report.totalCost().compareTo(BigDecimal.ZERO) > 0);
    }
}
