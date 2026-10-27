package com.affiliate.platform.web;

import com.affiliate.platform.integration.*;
import com.affiliate.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一媒体变现 REST 控制器单元测试
 */
class MediaPlatformUnifiedControllerTest {

    private MediaPlatformConnectorRegistry registry;
    private MediaPlatformUnifiedController controller;

    private GoogleGamConnector gamConnector;
    private TikTokPangleConnector pangleConnector;
    private MetaAudienceNetworkConnector fanConnector;

    @BeforeEach
    void setUp() {
        gamConnector = new GoogleGamConnector();
        pangleConnector = new TikTokPangleConnector();
        fanConnector = new MetaAudienceNetworkConnector();

        registry = new MediaPlatformConnectorRegistry(List.of(gamConnector, pangleConnector, fanConnector));
        controller = new MediaPlatformUnifiedController(registry);
        TenantContext.set("tenant_media_test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("测试列出支持的媒体与流量变现网络")
    void testListSupportedNetworks() {
        ResponseEntity<Set<String>> resp = controller.listSupportedNetworks();
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("GAM"));
        assertTrue(resp.getBody().contains("PANGLE"));
        assertTrue(resp.getBody().contains("AUDIENCE_NETWORK"));
    }

    @Test
    @DisplayName("测试统一触发媒体库存同步接口")
    void testUnifiedTriggerSync() throws Exception {
        // 1. GAM 同步
        var gamReq = new MediaPlatformUnifiedController.UnifiedMediaSyncRequest(
                null, "88889999", "conn-gam-sync", "valid_token", Map.of("mockMode", "true")
        );
        CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> gamFuture = controller.triggerSync("gam", gamReq);
        ResponseEntity<AdPlatformConnector.SyncResult> gamResp = gamFuture.get();
        assertEquals(200, gamResp.getStatusCode().value());
        assertEquals("GAM", gamResp.getBody().provider());
        assertTrue(gamResp.getBody().imported() > 0);

        // 2. TikTok Pangle 同步
        var pangleReq = new MediaPlatformUnifiedController.UnifiedMediaSyncRequest(
                "pg_app_1001", null, "conn-pangle-sync", "mock_token", Map.of("mockMode", "true")
        );
        CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> pangleFuture = controller.triggerSync("pangle", pangleReq);
        ResponseEntity<AdPlatformConnector.SyncResult> pangleResp = pangleFuture.get();
        assertEquals(200, pangleResp.getStatusCode().value());
        assertEquals("PANGLE", pangleResp.getBody().provider());
        assertTrue(pangleResp.getBody().imported() > 0);

        // 3. Meta Audience Network 同步
        var fanReq = new MediaPlatformUnifiedController.UnifiedMediaSyncRequest(
                "fan_app_2002", null, "conn-fan-sync", "mock_token", Map.of("mockMode", "true")
        );
        CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> fanFuture = controller.triggerSync("audience_network", fanReq);
        ResponseEntity<AdPlatformConnector.SyncResult> fanResp = fanFuture.get();
        assertEquals(200, fanResp.getStatusCode().value());
        assertEquals("AUDIENCE_NETWORK", fanResp.getBody().provider());
        assertTrue(fanResp.getBody().imported() > 0);
    }

    @Test
    @DisplayName("测试统一查询媒体收益与报表接口")
    void testUnifiedGetPerformance() throws Exception {
        // GAM
        CompletableFuture<ResponseEntity<AdPerformanceReport>> gamFuture =
                controller.getPerformance("gam", "network-8888", "2026-10-27", "valid_token");
        ResponseEntity<AdPerformanceReport> gamResp = gamFuture.get();
        assertEquals(200, gamResp.getStatusCode().value());
        assertEquals("GAM", gamResp.getBody().platform());
        assertTrue(gamResp.getBody().totalImpressions() > 0);

        // Pangle
        CompletableFuture<ResponseEntity<AdPerformanceReport>> pangleFuture =
                controller.getPerformance("tiktok_pangle", "app-pangle-1", "2026-10-27", "mock_token");
        ResponseEntity<AdPerformanceReport> pangleResp = pangleFuture.get();
        assertEquals(200, pangleResp.getStatusCode().value());
        assertEquals("PANGLE", pangleResp.getBody().platform());
        assertTrue(pangleResp.getBody().totalImpressions() > 0);

        // Audience Network
        CompletableFuture<ResponseEntity<AdPerformanceReport>> fanFuture =
                controller.getPerformance("fan", "app-fan-2", "2026-10-27", "mock_token");
        ResponseEntity<AdPerformanceReport> fanResp = fanFuture.get();
        assertEquals(200, fanResp.getStatusCode().value());
        assertEquals("AUDIENCE_NETWORK", fanResp.getBody().platform());
        assertTrue(fanResp.getBody().totalImpressions() > 0);
    }
}
