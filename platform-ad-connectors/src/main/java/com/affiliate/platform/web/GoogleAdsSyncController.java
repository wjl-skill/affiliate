package com.affiliate.platform.web;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.integration.AdPlatformConnector;
import com.affiliate.platform.integration.GoogleAdsApiClient;
import com.affiliate.platform.integration.GoogleAdsConnector;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Google Ads 同步与离线转化上报控制器 (Google Ads Campaign Sync & Conversion REST Controller)
 */
@RestController
@RequestMapping("/api/v1/google/ads")
public class GoogleAdsSyncController {

    private final GoogleAdsConnector connector;
    private final GoogleAdsApiClient apiClient;

    public GoogleAdsSyncController(GoogleAdsConnector connector, GoogleAdsApiClient apiClient) {
        this.connector = connector;
        this.apiClient = apiClient;
    }

    /**
     * 手动触发 Google Ads 广告活动与报表异步同步
     * POST /api/v1/google/ads/sync
     */
    @PostMapping("/sync")
    public CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> triggerSync(
            @RequestBody(required = false) SyncTriggerRequest request
    ) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        String customerId = (request != null && request.customerId() != null && !request.customerId().isBlank())
                ? request.customerId()
                : "1234567890";
        String connectionId = (request != null && request.connectionId() != null)
                ? request.connectionId()
                : "conn-google-" + customerId;

        Map<String, String> settings = new HashMap<>();
        settings.put("customerId", customerId);
        if (request != null) {
            if (request.accessToken() != null) settings.put("accessToken", request.accessToken());
            if (request.refreshToken() != null) settings.put("refreshToken", request.refreshToken());
        }

        PartnerConnection connection = new PartnerConnection(
                connectionId,
                tenantId,
                "Google Ads Manual Sync",
                SupplyType.DSP,
                "https://googleads.googleapis.com",
                settings,
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        return connector.sync(connection)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.internalServerError().body(
                        new AdPlatformConnector.SyncResult("GOOGLE_ADS", 0, 1, "Sync failed: " + ex.getMessage())
                ));
    }

    /**
     * 查询广告系列实时投放效果报表
     * GET /api/v1/google/ads/performance
     */
    @GetMapping("/performance")
    public ResponseEntity<List<GoogleAdsApiClient.CampaignPerformanceRecord>> getPerformance(
            @RequestParam(defaultValue = "123-456-7890") String customerId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "mock_token") String accessToken
    ) {
        String queryDate = (date != null && !date.isBlank()) ? date : LocalDate.now().toString();
        List<GoogleAdsApiClient.CampaignPerformanceRecord> records =
                apiClient.queryCampaignPerformance(customerId, accessToken, queryDate);
        return ResponseEntity.ok(records);
    }

    /**
     * 上报 S2S / 增强型线下转化事件 (Enhanced Conversions)
     * POST /api/v1/google/ads/conversions
     */
    @PostMapping("/conversions")
    public ResponseEntity<GoogleAdsApiClient.ConversionUploadResult> uploadConversion(
            @Valid @RequestBody ConversionUploadRequest request
    ) {
        String token = (request.accessToken() != null && !request.accessToken().isBlank())
                ? request.accessToken()
                : "mock_token";

        String conversionTime = (request.conversionTime() != null && !request.conversionTime().isBlank())
                ? request.conversionTime()
                : Instant.now().toString();

        GoogleAdsApiClient.ConversionUploadPayload payload = new GoogleAdsApiClient.ConversionUploadPayload(
                request.conversionActionId(),
                request.orderId(),
                request.value(),
                request.currency() != null ? request.currency() : "USD",
                conversionTime,
                request.gclid(),
                request.email(),
                request.phone()
        );

        GoogleAdsApiClient.ConversionUploadResult result =
                apiClient.uploadEnhancedConversion(request.customerId(), token, payload);
        return ResponseEntity.ok(result);
    }

    public record SyncTriggerRequest(
            String customerId,
            String connectionId,
            String accessToken,
            String refreshToken
    ) {}

    public record ConversionUploadRequest(
            @NotBlank String customerId,
            String accessToken,
            @NotBlank String conversionActionId,
            @NotBlank String orderId,
            @NotNull BigDecimal value,
            String currency,
            String conversionTime,
            String gclid,
            String email,
            String phone
    ) {}
}
