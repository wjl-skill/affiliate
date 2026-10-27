package com.affiliate.platform.web;

import com.affiliate.platform.integration.*;
import com.affiliate.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AdPlatformRegistryAndUnifiedControllerTest {

    private AdPlatformConnectorRegistry registry;
    private AdPlatformUnifiedController controller;

    private GoogleAdsConnector googleConnector;
    private TikTokAdsConnector tiktokConnector;
    private FacebookAdsConnector facebookConnector;

    private GoogleOAuthClient googleOAuth;
    private TikTokOAuthClient tiktokOAuth;
    private FacebookOAuthClient facebookOAuth;

    @BeforeEach
    void setUp() {
        googleOAuth = new GoogleOAuthClient();
        tiktokOAuth = new TikTokOAuthClient();
        facebookOAuth = new FacebookOAuthClient();

        googleConnector = new GoogleAdsConnector(new GoogleAdsApiClient(), googleOAuth);
        tiktokConnector = new TikTokAdsConnector(new TikTokAdsApiClient(), tiktokOAuth);
        facebookConnector = new FacebookAdsConnector(new FacebookAdsApiClient(), facebookOAuth);

        registry = new AdPlatformConnectorRegistry(
                List.of(googleConnector, tiktokConnector, facebookConnector),
                List.of(googleOAuth, tiktokOAuth, facebookOAuth)
        );

        controller = new AdPlatformUnifiedController(registry);
        TenantContext.set("tenant_unified_test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("测试注册中心平台别名归一化与连接器查找")
    void testRegistryAliasNormalization() {
        Set<String> supported = registry.getSupportedPlatforms();
        assertTrue(supported.contains("GOOGLE_ADS"));
        assertTrue(supported.contains("TIKTOK"));
        assertTrue(supported.contains("FACEBOOK"));

        // 别名测试
        assertSame(tiktokConnector, registry.getConnector("tt"));
        assertSame(tiktokConnector, registry.getConnector("TIKTOK"));
        assertSame(tiktokConnector, registry.getConnector("TIKTOK_ADS"));

        assertSame(facebookConnector, registry.getConnector("fb"));
        assertSame(facebookConnector, registry.getConnector("meta"));
        assertSame(facebookConnector, registry.getConnector("FACEBOOK"));

        assertSame(googleConnector, registry.getConnector("google"));
        assertSame(googleConnector, registry.getConnector("GOOGLE_ADS"));
        assertSame(googleConnector, registry.getConnector("gads"));

        // 未知平台
        assertThrows(IllegalArgumentException.class, () -> registry.getConnector("unknown_ad_platform"));
    }

    @Test
    @DisplayName("测试统一控制器 listSupportedPlatforms 接口")
    void testListSupportedPlatforms() {
        ResponseEntity<Set<String>> resp = controller.listSupportedPlatforms();
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("TIKTOK"));
        assertTrue(resp.getBody().contains("FACEBOOK"));
        assertTrue(resp.getBody().contains("GOOGLE_ADS"));
    }

    @Test
    @DisplayName("测试统一控制器 triggerSync 支持各平台")
    void testUnifiedTriggerSync() throws Exception {
        // 1. TikTok 同步
        var ttReq = new AdPlatformUnifiedController.UnifiedSyncRequest("7100000000001", "conn-tt", "mock_token", null);
        CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> ttFuture = controller.triggerSync("tt", ttReq);
        ResponseEntity<AdPlatformConnector.SyncResult> ttResp = ttFuture.get();
        assertEquals(200, ttResp.getStatusCode().value());
        assertEquals("TIKTOK", ttResp.getBody().provider());
        assertTrue(ttResp.getBody().imported() > 0);

        // 2. Facebook 同步
        var fbReq = new AdPlatformUnifiedController.UnifiedSyncRequest("10000000001", "conn-fb", "mock_token", null);
        CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> fbFuture = controller.triggerSync("fb", fbReq);
        ResponseEntity<AdPlatformConnector.SyncResult> fbResp = fbFuture.get();
        assertEquals(200, fbResp.getStatusCode().value());
        assertEquals("FACEBOOK", fbResp.getBody().provider());
        assertTrue(fbResp.getBody().imported() > 0);

        // 3. Google 同步
        var gReq = new AdPlatformUnifiedController.UnifiedSyncRequest("123-456-7890", "conn-google", "mock_token", null);
        CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> gFuture = controller.triggerSync("google", gReq);
        ResponseEntity<AdPlatformConnector.SyncResult> gResp = gFuture.get();
        assertEquals(200, gResp.getStatusCode().value());
        assertEquals("GOOGLE_ADS", gResp.getBody().provider());
        assertTrue(gResp.getBody().imported() > 0);
    }

    @Test
    @DisplayName("测试统一控制器 getPerformance 报表聚合检索")
    void testUnifiedGetPerformance() throws Exception {
        CompletableFuture<ResponseEntity<AdPerformanceReport>> future =
                controller.getPerformance("meta", "act_888999", "2026-10-20", "mock_token");
        ResponseEntity<AdPerformanceReport> resp = future.get();

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("FACEBOOK", resp.getBody().platform());
        assertTrue(resp.getBody().totalImpressions() > 0);
        assertFalse(resp.getBody().records().isEmpty());
    }

    @Test
    @DisplayName("测试统一控制器 uploadConversion 跨平台转化事件上报")
    void testUnifiedUploadConversion() throws Exception {
        var req = new AdPlatformUnifiedController.UnifiedConversionRequest(
                "pixel_global_101",
                "mock_token",
                "PURCHASE",
                "order_unified_777",
                new BigDecimal("199.00"),
                "USD",
                null,
                "gclid_123",
                "fbclid_456",
                "fbp_test",
                "fbc_test",
                "ttclid_789",
                "buyer@test.com",
                "+12345678901",
                "127.0.0.1",
                "Chrome/Agent",
                null
        );

        // TikTok 上报
        CompletableFuture<ResponseEntity<ConversionUploadResult>> ttFuture = controller.uploadConversion("tt", req);
        ResponseEntity<ConversionUploadResult> ttResp = ttFuture.get();
        assertEquals(200, ttResp.getStatusCode().value());
        assertTrue(ttResp.getBody().success());
        assertEquals("TIKTOK", ttResp.getBody().platform());

        // Meta/Facebook 上报
        CompletableFuture<ResponseEntity<ConversionUploadResult>> fbFuture = controller.uploadConversion("facebook", req);
        ResponseEntity<ConversionUploadResult> fbResp = fbFuture.get();
        assertEquals(200, fbResp.getStatusCode().value());
        assertTrue(fbResp.getBody().success());
        assertEquals("FACEBOOK", fbResp.getBody().platform());

        // Google 上报
        CompletableFuture<ResponseEntity<ConversionUploadResult>> gFuture = controller.uploadConversion("google", req);
        ResponseEntity<ConversionUploadResult> gResp = gFuture.get();
        assertEquals(200, gResp.getStatusCode().value());
        assertTrue(gResp.getBody().success());
        assertEquals("GOOGLE_ADS", gResp.getBody().platform());
    }

    @Test
    @DisplayName("测试统一控制器 OAuth 握手授权与回调")
    void testUnifiedOAuthEndpoints() {
        ResponseEntity<AdPlatformOAuthClient.AuthorizationRequest> authResp = controller.authorize("tiktok");
        assertEquals(200, authResp.getStatusCode().value());
        assertNotNull(authResp.getBody().url());
        String state = authResp.getBody().state();
        assertNotNull(state);

        ResponseEntity<AdPlatformOAuthClient.TokenResponse> tokenResp = controller.callback("tiktok", "test_code", state);
        assertEquals(200, tokenResp.getStatusCode().value());
        assertEquals("stored-server-side", tokenResp.getBody().accessToken());
    }
}
