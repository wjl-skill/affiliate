package com.affiliate.platform.integration;

import com.affiliate.platform.domain.PartnerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Meta / Facebook 广告平台连接器实现 (Facebook Ads Platform Connector)
 * <p>
 * 实现 {@link AdPlatformConnector} 规范：
 * 1. 识别提供商标识为 "FACEBOOK"；
 * 2. 具备 Insights API 广告系列成效拉取与 System User Token 长期凭据支持；
 * 3. 支持 Meta Conversions API (CAPI) S2S 服务端高级匹配转化上报。
 */
@Component
public class FacebookAdsConnector implements AdPlatformConnector {

    private static final Logger log = LoggerFactory.getLogger(FacebookAdsConnector.class);
    private final FacebookAdsApiClient apiClient;
    private final FacebookOAuthClient oAuthClient;

    public FacebookAdsConnector() {
        this(new FacebookAdsApiClient(), null);
    }

    public FacebookAdsConnector(FacebookAdsApiClient apiClient) {
        this(apiClient, null);
    }

    @Autowired
    public FacebookAdsConnector(FacebookAdsApiClient apiClient,
                                @Autowired(required = false) FacebookOAuthClient oAuthClient) {
        this.apiClient = apiClient != null ? apiClient : new FacebookAdsApiClient();
        this.oAuthClient = oAuthClient;
    }

    @Override
    public String provider() {
        return "FACEBOOK";
    }

    @Override
    public CompletableFuture<SyncResult> sync(PartnerConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[FacebookAdsConnector] Starting sync job for partner connection: {}", connection != null ? connection.id() : "null");

            Map<String, String> settings = connection != null && connection.settings() != null ? connection.settings() : Map.of();
            String accountId = settings.getOrDefault("accountId", settings.getOrDefault("adAccountId", "10000000001"));
            String refreshToken = settings.get("refreshToken");
            String accessToken = settings.get("accessToken");

            if ((accessToken == null || accessToken.isBlank()) && refreshToken != null && !refreshToken.isBlank() && oAuthClient != null) {
                try {
                    log.info("[FacebookAdsConnector] Refreshing/extending access token via FacebookOAuthClient...");
                    AdPlatformOAuthClient.TokenResponse tokenResp = oAuthClient.refreshToken(refreshToken);
                    accessToken = tokenResp.accessToken();
                } catch (Exception e) {
                    log.error("[FacebookAdsConnector] Failed to refresh token: {}", e.getMessage());
                }
            }

            if (accessToken == null || accessToken.isBlank()) {
                accessToken = "mock_secure_facebook_token";
            }

            try {
                String today = LocalDate.now().toString();
                List<AdPerformanceRecord> metrics = apiClient.queryCampaignPerformance(accountId, accessToken, today);

                int imported = metrics.size();
                int rejected = 0;
                String msg = String.format("Successfully synced %d campaigns from Meta ad account [%s]", imported, accountId);

                log.info("[FacebookAdsConnector] Sync completed successfully: {}", msg);
                return new SyncResult(provider(), imported, rejected, msg);
            } catch (Exception e) {
                log.error("[FacebookAdsConnector] Sync failed for account {}: {}", accountId, e.getMessage());
                return new SyncResult(provider(), 0, 1, "Sync error: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<AdPerformanceReport> queryPerformance(AdPerformanceQuery query) {
        return CompletableFuture.supplyAsync(() -> {
            String accountId = query != null ? query.accountId() : "10000000001";
            String token = query != null && query.accessToken() != null ? query.accessToken() : "mock_token";
            String date = query != null && query.startDate() != null ? query.startDate() : LocalDate.now().toString();

            List<AdPerformanceRecord> records = apiClient.queryCampaignPerformance(accountId, token, date);
            return AdPerformanceReport.of(provider(), accountId, records);
        });
    }

    @Override
    public CompletableFuture<ConversionUploadResult> uploadConversion(UnifiedConversionPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            if (payload == null) {
                return ConversionUploadResult.fail(provider(), "unknown", "Payload is null", null);
            }

            String token = payload.accessToken() != null && !payload.accessToken().isBlank()
                    ? payload.accessToken()
                    : "mock_token";

            return apiClient.uploadConversionsApi(payload.accountOrPixelId(), token, payload);
        });
    }
}
