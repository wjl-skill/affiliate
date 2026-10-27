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
 * Google Ads 平台连接器及底层 API 客户端集成测试
 */
class GoogleAdsConnectorTest {

    @Test
    @DisplayName("测试 OAuth 令牌响应解析与过期判定逻辑")
    void testTokenResponseParsingAndExpiration() {
        String mockJson = """
                {
                  "access_token": "ya29.a0AfH6SMC...",
                  "expires_in": 3600,
                  "refresh_token": "1//04...",
                  "scope": "https://www.googleapis.com/auth/adwords",
                  "token_type": "Bearer"
                }
                """;

        GoogleOAuthClient.TokenResponse token = GoogleOAuthClient.TokenResponse.of(mockJson, Instant.now());

        assertNotNull(token);
        assertEquals("ya29.a0AfH6SMC...", token.accessToken());
        assertEquals("1//04...", token.refreshToken());
        assertEquals(3600, token.expiresIn());
        assertEquals("Bearer", token.tokenType());
        assertFalse(token.isExpired(), "Fresh token should not be expired");

        // 测试已过期 token
        GoogleOAuthClient.TokenResponse expiredToken = GoogleOAuthClient.TokenResponse.of(mockJson, Instant.now().minusSeconds(3600));
        assertTrue(expiredToken.isExpired(), "Token older than expiresIn should be expired");
    }

    @Test
    @DisplayName("测试 GoogleAdsApiClient 的 Campaign 效果指标检索与增强型转化上报")
    void testGoogleAdsApiClientPerformanceAndConversion() {
        GoogleAdsApiClient client = new GoogleAdsApiClient();

        // 1. 模拟查询 Campaign 效果
        List<GoogleAdsApiClient.CampaignPerformanceRecord> metrics =
                client.queryCampaignPerformance("123-456-7890", "test_token", LocalDate.now().toString());

        assertNotNull(metrics);
        assertFalse(metrics.isEmpty());
        assertTrue(metrics.getFirst().impressions() > 0);
        assertTrue(metrics.getFirst().cost().compareTo(BigDecimal.ZERO) > 0);

        // 2. 模拟增强转化上传载荷
        GoogleAdsApiClient.ConversionUploadPayload conversion = new GoogleAdsApiClient.ConversionUploadPayload(
                "action_1001", "order_888999", new BigDecimal("99.90"), "USD", "2026-09-20 12:00:00+00:00"
        );
        GoogleAdsApiClient.ConversionUploadResult result = client.uploadEnhancedConversion("123-456-7890", "test_token", conversion);
        assertNotNull(result);
    }

    @Test
    @DisplayName("测试 GoogleAdsConnector 平台连接器的全链路异步同步任务")
    void testGoogleAdsConnectorSync() throws Exception {
        GoogleAdsApiClient apiClient = new GoogleAdsApiClient();
        GoogleOAuthClient oAuthClient = new GoogleOAuthClient("client_id", "client_secret", "http://localhost/callback", List.of("scope"));

        GoogleAdsConnector connector = new GoogleAdsConnector(apiClient, oAuthClient);

        assertEquals("GOOGLE_ADS", connector.provider());

        PartnerConnection connection = new PartnerConnection(
                "conn-google-01",
                "Google Ads Main",
                SupplyType.DSP,
                "https://googleads.googleapis.com",
                Map.of("customerId", "999-888-7777", "refreshToken", "1//refresh_token_test"),
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        CompletableFuture<AdPlatformConnector.SyncResult> future = connector.sync(connection);
        AdPlatformConnector.SyncResult syncResult = future.get();

        assertNotNull(syncResult);
        assertEquals("GOOGLE_ADS", syncResult.provider());
        assertTrue(syncResult.imported() > 0, "Should import campaigns from Google Ads");
        assertEquals(0, syncResult.rejected());
    }

    @Test
    @DisplayName("测试 GoogleAdsApiClient 真实 SearchStream JSON 响应解析")
    void testSearchStreamJsonParsing() {
        GoogleAdsApiClient client = new GoogleAdsApiClient();
        String json = """
                [
                  {
                    "results": [
                      {
                        "campaign": {
                          "resourceName": "customers/1234567890/campaigns/555123",
                          "id": "555123",
                          "name": "Live Search Campaign Q4",
                          "status": "ENABLED"
                        },
                        "metrics": {
                          "impressions": "25000",
                          "clicks": "850",
                          "conversions": 42.0,
                          "costMicros": "320000000"
                        },
                        "segments": {
                          "date": "2026-10-08"
                        }
                      }
                    ]
                  }
                ]
                """;

        List<GoogleAdsApiClient.CampaignPerformanceRecord> records =
                client.parsePerformanceRecords(json, "1234567890");

        assertNotNull(records);
        assertEquals(1, records.size());
        GoogleAdsApiClient.CampaignPerformanceRecord r = records.get(0);
        assertEquals("555123", r.campaignId());
        assertEquals("Live Search Campaign Q4", r.campaignName());
        assertEquals(25000L, r.impressions());
        assertEquals(850L, r.clicks());
        assertEquals(42L, r.conversions());
        assertEquals(new BigDecimal("320.00"), r.cost());
        assertEquals("2026-10-08", r.date());
    }
}
