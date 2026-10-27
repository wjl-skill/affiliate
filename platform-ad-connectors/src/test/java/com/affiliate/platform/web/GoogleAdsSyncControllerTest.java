package com.affiliate.platform.web;

import com.affiliate.platform.integration.AdPlatformConnector;
import com.affiliate.platform.integration.GoogleAdsApiClient;
import com.affiliate.platform.integration.GoogleAdsConnector;
import com.affiliate.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoogleAdsSyncControllerTest {

    private GoogleAdsConnector connector;
    private GoogleAdsApiClient apiClient;
    private GoogleAdsSyncController controller;

    @BeforeEach
    void setUp() {
        connector = mock(GoogleAdsConnector.class);
        apiClient = mock(GoogleAdsApiClient.class);
        controller = new GoogleAdsSyncController(connector, apiClient);
        TenantContext.set("tenant_google_ads");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testTriggerSyncSuccess() throws Exception {
        AdPlatformConnector.SyncResult syncResult = new AdPlatformConnector.SyncResult("GOOGLE_ADS", 5, 0, "OK");
        when(connector.sync(any())).thenReturn(CompletableFuture.completedFuture(syncResult));

        GoogleAdsSyncController.SyncTriggerRequest request = new GoogleAdsSyncController.SyncTriggerRequest(
                "123-456-7890", "conn-01", "token123", "refresh123"
        );

        CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> future = controller.triggerSync(request);
        ResponseEntity<AdPlatformConnector.SyncResult> response = future.get();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(5, response.getBody().imported());
        verify(connector, times(1)).sync(any());
    }

    @Test
    void testGetPerformance() {
        List<GoogleAdsApiClient.CampaignPerformanceRecord> mockRecords = List.of(
                new GoogleAdsApiClient.CampaignPerformanceRecord("c1", "Campaign 1", "1234567890", 1000L, 50L, 5L, new BigDecimal("25.00"), "2026-10-15")
        );
        when(apiClient.queryCampaignPerformance(any(), any(), any())).thenReturn(mockRecords);

        ResponseEntity<List<GoogleAdsApiClient.CampaignPerformanceRecord>> response =
                controller.getPerformance("123-456-7890", "2026-10-15", "token");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Campaign 1", response.getBody().get(0).campaignName());
    }

    @Test
    void testUploadConversion() {
        GoogleAdsApiClient.ConversionUploadResult mockResult =
                new GoogleAdsApiClient.ConversionUploadResult(true, "Uploaded order_999", "{}");
        when(apiClient.uploadEnhancedConversion(any(), any(), any())).thenReturn(mockResult);

        GoogleAdsSyncController.ConversionUploadRequest request = new GoogleAdsSyncController.ConversionUploadRequest(
                "123-456-7890",
                "test_token",
                "act_101",
                "order_999",
                new BigDecimal("49.99"),
                "USD",
                "2026-10-15T10:00:00Z",
                "gclid_abc123",
                "user@example.com",
                "+1234567890"
        );

        ResponseEntity<GoogleAdsApiClient.ConversionUploadResult> response = controller.uploadConversion(request);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertEquals("Uploaded order_999", response.getBody().message());
        verify(apiClient, times(1)).uploadEnhancedConversion(eq("123-456-7890"), eq("test_token"), any());
    }
}
