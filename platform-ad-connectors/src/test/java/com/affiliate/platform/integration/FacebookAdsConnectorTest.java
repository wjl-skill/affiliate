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
 * Meta / Facebook 广告平台连接器与 API 客户端集成测试
 */
class FacebookAdsConnectorTest {

    @Test
    @DisplayName("测试 Meta OAuth 与长期 System User Token 机制")
    void testFacebookOAuthFlow() {
        FacebookOAuthClient oAuthClient = new FacebookOAuthClient(
                "app_fb_9988", "secret_fb_secret", "http://localhost:8080/api/v1/ad-platforms/facebook/oauth/callback"
        );

        assertEquals("FACEBOOK", oAuthClient.platform());

        var authReq = oAuthClient.authorizationUrl("tenant-fb-01");
        assertNotNull(authReq);
        assertTrue(authReq.url().toString().contains("client_id=app_fb_9988"));
        assertTrue(authReq.state().startsWith("tenant-fb-01."));

        assertTrue(oAuthClient.consumeState(authReq.state(), "tenant-fb-01"));
        assertFalse(oAuthClient.consumeState(authReq.state(), "tenant-fb-01"));

        var token = oAuthClient.exchangeCode("mock_code");
        assertNotNull(token);
        assertNotNull(token.accessToken());

        var extended = oAuthClient.refreshToken("mock_short_token");
        assertNotNull(extended);
    }

    @Test
    @DisplayName("测试 Meta Insights 报表检索与 CAPI 转化上报")
    void testFacebookApiClient() {
        FacebookAdsApiClient client = new FacebookAdsApiClient();

        // 1. 查询投放效果指标
        List<AdPerformanceRecord> records = client.queryCampaignPerformance(
                "10000000001", "mock_token", LocalDate.now().toString()
        );
        assertNotNull(records);
        assertFalse(records.isEmpty());
        assertEquals("FACEBOOK", records.getFirst().platform());
        assertTrue(records.getFirst().impressions() > 0);

        // 2. 模拟 CAPI 转化上传
        UnifiedConversionPayload payload = new UnifiedConversionPayload(
                "FACEBOOK",
                "pixel_fb_12345",
                "mock_token",
                "PURCHASE",
                "order_fb_2002",
                new BigDecimal("129.99"),
                "USD",
                Instant.now(),
                null,
                "fbclid_xyz_789",
                "fb.1.1690000000.fbp_id",
                "fb.1.1690000000.fbclid_xyz_789",
                null,
                "customer@facebook.com",
                "+1987654321",
                "10.0.0.1",
                "Mozilla/5.0 MetaBrowser",
                Map.of()
        );

        ConversionUploadResult result = client.uploadConversionsApi("pixel_fb_12345", "mock_token", payload);
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("FACEBOOK", result.platform());
        assertEquals("order_fb_2002", result.orderId());
    }

    @Test
    @DisplayName("测试 FacebookAdsConnector 全链路异步同步与 CAPI 上报")
    void testFacebookAdsConnectorSyncAndCapi() throws Exception {
        FacebookAdsApiClient apiClient = new FacebookAdsApiClient();
        FacebookOAuthClient oAuthClient = new FacebookOAuthClient();
        FacebookAdsConnector connector = new FacebookAdsConnector(apiClient, oAuthClient);

        assertEquals("FACEBOOK", connector.provider());

        PartnerConnection conn = new PartnerConnection(
                "conn-fb-01",
                "tenant-fb",
                "Facebook Advantage Campaign",
                SupplyType.DSP,
                "https://graph.facebook.com",
                Map.of("accountId", "act_555444333", "accessToken", "mock_token"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        CompletableFuture<AdPlatformConnector.SyncResult> syncFuture = connector.sync(conn);
        AdPlatformConnector.SyncResult syncResult = syncFuture.get();
        assertNotNull(syncResult);
        assertEquals("FACEBOOK", syncResult.provider());
        assertTrue(syncResult.imported() > 0);

        // 统一转化上报
        UnifiedConversionPayload payload = new UnifiedConversionPayload(
                "FACEBOOK",
                "pixel_fb_555",
                "mock_token",
                "LEAD",
                "order_fb_lead_01",
                BigDecimal.ZERO,
                "USD",
                Instant.now(),
                null,
                "fbclid_lead",
                null,
                null,
                null,
                "lead@example.com",
                null,
                null,
                null,
                Map.of()
        );

        CompletableFuture<ConversionUploadResult> convFuture = connector.uploadConversion(payload);
        ConversionUploadResult convResult = convFuture.get();
        assertNotNull(convResult);
        assertTrue(convResult.success());
    }
}
