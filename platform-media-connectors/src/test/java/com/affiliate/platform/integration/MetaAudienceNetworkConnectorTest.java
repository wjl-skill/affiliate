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
 * Meta Audience Network (FAN) 媒体与流量变现网络连接器单元测试
 */
class MetaAudienceNetworkConnectorTest {

    @Test
    @DisplayName("测试 Meta Audience Network 正常同步版位与竞价单元")
    void testFanSyncSuccess() throws Exception {
        MetaAudienceNetworkConnector connector = new MetaAudienceNetworkConnector();

        assertEquals("AUDIENCE_NETWORK", connector.provider());
        assertEquals(MetaAudienceNetworkConnector.CircuitStatus.HEALTHY, connector.getCircuitStatus());

        PartnerConnection connection = new PartnerConnection(
                "conn-fan-01",
                "Meta Audience Network App",
                SupplyType.SSP,
                "https://graph.facebook.com",
                Map.of("appId", "fan_app_9988", "apiToken", "mock_fan_system_token"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        CompletableFuture<AdPlatformConnector.SyncResult> future = connector.sync(connection);
        AdPlatformConnector.SyncResult result = future.get();

        assertNotNull(result);
        assertEquals("AUDIENCE_NETWORK", result.provider());
        assertTrue(result.imported() > 0, "FAN sync should import placements and bidding units");
        assertEquals(0, result.rejected());
        assertEquals(MetaAudienceNetworkConnector.CircuitStatus.HEALTHY, connector.getCircuitStatus());
    }

    @Test
    @DisplayName("测试 Meta Audience Network 连续失败触发断路器熔断")
    void testFanCircuitBreakerTripping() throws Exception {
        MetaAudienceNetworkConnector connector = new MetaAudienceNetworkConnector();

        PartnerConnection errorConnection = new PartnerConnection(
                "conn-fan-err",
                "Meta FAN Publisher Err",
                SupplyType.SSP,
                "https://graph.facebook.com",
                Map.of("appId", "fan_app_err", "apiToken", "force_error"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        for (int i = 0; i < 3; i++) {
            connector.sync(errorConnection).get();
        }
        assertEquals(MetaAudienceNetworkConnector.CircuitStatus.DEGRADED, connector.getCircuitStatus());

        for (int i = 0; i < 2; i++) {
            connector.sync(errorConnection).get();
        }
        assertEquals(MetaAudienceNetworkConnector.CircuitStatus.CIRCUIT_OPEN, connector.getCircuitStatus());

        AdPlatformConnector.SyncResult rejected = connector.sync(errorConnection).get();
        assertTrue(rejected.message().contains("Circuit breaker is OPEN"));
    }

    @Test
    @DisplayName("测试 Meta Audience Network 缺失认证凭据时明确拦截报错")
    void testFanMissingCredentialsFails() throws Exception {
        MetaAudienceNetworkConnector connector = new MetaAudienceNetworkConnector();

        PartnerConnection emptyConnection = new PartnerConnection(
                "conn-fan-no-cred",
                "Meta FAN Publisher Empty",
                SupplyType.SSP,
                "https://graph.facebook.com",
                Map.of(),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        AdPlatformConnector.SyncResult result = connector.sync(emptyConnection).get();
        assertNotNull(result);
        assertEquals(0, result.imported());
        assertEquals(1, result.rejected());
        assertTrue(result.message().contains("Missing required Meta Audience Network credentials"));
    }

    @Test
    @DisplayName("测试 Meta Audience Network 变现报表检索")
    void testFanPerformanceQuery() throws Exception {
        MetaAudienceNetworkConnector connector = new MetaAudienceNetworkConnector();
        AdPerformanceQuery query = new AdPerformanceQuery("fan_app_777", "mock_token", "2026-10-27");

        AdPerformanceReport report = connector.queryPerformance(query).get();
        assertNotNull(report);
        assertEquals("AUDIENCE_NETWORK", report.platform());
        assertEquals("fan_app_777", report.accountId());
        assertFalse(report.records().isEmpty());
        assertTrue(report.totalImpressions() > 0);
        assertTrue(report.totalCost().compareTo(BigDecimal.ZERO) > 0);
    }
}
