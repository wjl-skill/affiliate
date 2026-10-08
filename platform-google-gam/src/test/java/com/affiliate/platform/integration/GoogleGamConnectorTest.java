package com.affiliate.platform.integration;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Google Ad Manager (GAM) 连接器与熔断机制集成测试
 */
class GoogleGamConnectorTest {

    @Test
    @DisplayName("测试 GAM 平台连接器正常同步广告单元与投放项")
    void testGamSyncSuccess() throws Exception {
        GoogleGamConnector connector = new GoogleGamConnector();

        assertEquals("GAM", connector.provider());
        assertEquals(GoogleGamConnector.CircuitStatus.HEALTHY, connector.getCircuitStatus());

        PartnerConnection connection = new PartnerConnection(
                "conn-gam-01",
                "Google Ad Manager Supply",
                SupplyType.SSP,
                "https://admanager.googleapis.com",
                Map.of("networkCode", "88889999", "apiToken", "valid_token"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        CompletableFuture<AdPlatformConnector.SyncResult> future = connector.sync(connection);
        AdPlatformConnector.SyncResult result = future.get();

        assertNotNull(result);
        assertEquals("GAM", result.provider());
        assertTrue(result.imported() > 0, "GAM sync should import inventory ad units and line items");
        assertEquals(0, result.rejected());
        assertEquals(GoogleGamConnector.CircuitStatus.HEALTHY, connector.getCircuitStatus());
    }

    @Test
    @DisplayName("测试 GAM 连接器连续失败自动降级并在达到阈值后熔断拦截请求")
    void testGamCircuitBreakerTripping() throws Exception {
        GoogleGamConnector connector = new GoogleGamConnector();

        PartnerConnection errorConnection = new PartnerConnection(
                "conn-gam-err",
                "Google Ad Manager Supply Err",
                SupplyType.SSP,
                "https://admanager.googleapis.com",
                Map.of("networkCode", "88889999", "apiToken", "force_error"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        // 连续触发 3 次失败，进入 DEGRADED 状态
        for (int i = 0; i < 3; i++) {
            connector.sync(errorConnection).get();
        }
        assertEquals(GoogleGamConnector.CircuitStatus.DEGRADED, connector.getCircuitStatus());

        // 连续触发满 5 次失败，断路器熔断进入 CIRCUIT_OPEN
        for (int i = 0; i < 2; i++) {
            connector.sync(errorConnection).get();
        }
        assertEquals(GoogleGamConnector.CircuitStatus.CIRCUIT_OPEN, connector.getCircuitStatus());

        // 验证熔断状态下请求被直接拦截拒绝
        AdPlatformConnector.SyncResult rejectedResult = connector.sync(errorConnection).get();
        assertTrue(rejectedResult.message().contains("Circuit breaker is OPEN"));
    }

    @Test
    @DisplayName("测试 GAM 连接器在缺少认证凭据时明确拒绝并报错失败")
    void testGamMissingCredentialsFails() throws Exception {
        GoogleGamConnector connector = new GoogleGamConnector();

        PartnerConnection unauthenticatedConnection = new PartnerConnection(
                "conn-gam-no-cred",
                "Google Ad Manager Supply Without Creds",
                SupplyType.SSP,
                "https://admanager.googleapis.com",
                Map.of(), // 空设置，缺少 networkCode 和 apiToken
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        CompletableFuture<AdPlatformConnector.SyncResult> future = connector.sync(unauthenticatedConnection);
        AdPlatformConnector.SyncResult result = future.get();

        assertNotNull(result);
        assertEquals(0, result.imported(), "缺少认证凭据时不应伪造成功导入数据");
        assertEquals(1, result.rejected());
        assertTrue(result.message().contains("Missing required GAM authentication credentials"));
    }
}
