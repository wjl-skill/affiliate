package com.affiliate.platform.integration;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TikTok for Business 广告平台连接器与 API 客户端集成测试
 */
class TikTokAdsConnectorTest {

    @Test
    @DisplayName("测试 TikTok OAuth 客户端授权跳转与单次 State 消费校验")
    void testTikTokOAuthFlow() {
        TikTokOAuthClient oAuthClient = new TikTokOAuthClient(
                "app_tt_123456", "secret_tt_abc", "http://localhost:8080/api/v1/ad-platforms/tiktok/oauth/callback"
        );

        assertEquals("TIKTOK", oAuthClient.platform());

        var authReq = oAuthClient.authorizationUrl("tenant-tt-01");
        assertNotNull(authReq);
        assertNotNull(authReq.url());
        assertTrue(authReq.url().toString().contains("app_id=app_tt_123456"));
        assertTrue(authReq.state().startsWith("tenant-tt-01."));

        // 验证 State 单次消费校验
        assertTrue(oAuthClient.consumeState(authReq.state(), "tenant-tt-01"));
        assertFalse(oAuthClient.consumeState(authReq.state(), "tenant-tt-01"), "State 不得二次重复消费");

        // 换取与刷新令牌
        var token = oAuthClient.exchangeCode("mock_code");
        assertNotNull(token);
        assertNotNull(token.accessToken());
        assertFalse(token.isExpired());

        var refreshed = oAuthClient.refreshToken("mock_refresh");
        assertNotNull(refreshed);
    }

    @Test
    @DisplayName("测试 TikTokAdsApiClient 的 Integrated Report 报表解析与 Events API 上报")
    void testTikTokApiClient() {
        TikTokAdsApiClient client = new TikTokAdsApiClient();

        // 1. 查询投放指标
        List<AdPerformanceRecord> records = client.queryCampaignPerformance(
                "7100000000000000001", "mock_token", LocalDate.now().toString()
        );
        assertNotNull(records);
        assertFalse(records.isEmpty());
        assertEquals("TIKTOK", records.getFirst().platform());
        assertTrue(records.getFirst().impressions() > 0);
        assertTrue(records.getFirst().cost().compareTo(BigDecimal.ZERO) > 0);

        // 2. 模拟 Events API 转化上传
        UnifiedConversionPayload payload = new UnifiedConversionPayload(
                "TIKTOK",
                "pixel_tt_999",
                "mock_token",
                "PURCHASE",
                "order_tt_1001",
                new BigDecimal("88.50"),
                "USD",
                Instant.now(),
                null,
                null,
                null,
                null,
                "ttclid_abc_123",
                "user@tiktok.com",
                "+1234567890",
                "192.168.1.1",
                "Mozilla/5.0 TikTokApp",
                Map.of()
        );

        ConversionUploadResult result = client.uploadEvents("pixel_tt_999", "mock_token", payload);
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("TIKTOK", result.platform());
        assertEquals("order_tt_1001", result.orderId());
    }

    @Test
    @DisplayName("测试 TikTokAdsConnector 全链路异步同步与聚合效果查询")
    void testTikTokAdsConnectorSyncAndPerformance() throws Exception {
        TikTokAdsApiClient apiClient = new TikTokAdsApiClient();
        TikTokOAuthClient oAuthClient = new TikTokOAuthClient();
        TikTokAdsConnector connector = new TikTokAdsConnector(apiClient, oAuthClient);

        assertEquals("TIKTOK", connector.provider());

        PartnerConnection conn = new PartnerConnection(
                "conn-tt-01",
                "tenant-tt",
                "TikTok Feed Campaign",
                SupplyType.DSP,
                "https://business-api.tiktok.com",
                Map.of("advertiserId", "7122222222222222222", "accessToken", "mock_token"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        CompletableFuture<AdPlatformConnector.SyncResult> syncFuture = connector.sync(conn);
        AdPlatformConnector.SyncResult syncResult = syncFuture.get();
        assertNotNull(syncResult);
        assertEquals("TIKTOK", syncResult.provider());
        assertTrue(syncResult.imported() > 0);

        // 聚合报表查询
        AdPerformanceQuery query = new AdPerformanceQuery("7122222222222222222", "mock_token", LocalDate.now().toString());
        CompletableFuture<AdPerformanceReport> reportFuture = connector.queryPerformance(query);
        AdPerformanceReport report = reportFuture.get();
        assertNotNull(report);
        assertEquals("TIKTOK", report.platform());
        assertTrue(report.totalImpressions() > 0);
        assertTrue(report.totalCost().compareTo(BigDecimal.ZERO) > 0);
    }
}
