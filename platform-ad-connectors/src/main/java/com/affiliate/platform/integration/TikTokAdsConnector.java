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
 * TikTok for Business 广告平台连接器实现 (TikTok Ads Platform Connector)
 * <p>
 * 实现 {@link AdPlatformConnector} 规范：
 * 1. 识别提供商标识为 "TIKTOK"；
 * 2. 支持 TikTok Integrated Report 报表指标拉取与自动 Token 刷新；
 * 3. 支持 TikTok Events API (S2S) 服务端转化事件上报。
 */
@Component
public class TikTokAdsConnector implements AdPlatformConnector {

    private static final Logger log = LoggerFactory.getLogger(TikTokAdsConnector.class);
    private final TikTokAdsApiClient apiClient;
    private final TikTokOAuthClient oAuthClient;

    public TikTokAdsConnector() {
        this(new TikTokAdsApiClient(), null);
    }

    public TikTokAdsConnector(TikTokAdsApiClient apiClient) {
        this(apiClient, null);
    }

    @Autowired
    public TikTokAdsConnector(TikTokAdsApiClient apiClient,
                              @Autowired(required = false) TikTokOAuthClient oAuthClient) {
        this.apiClient = apiClient != null ? apiClient : new TikTokAdsApiClient();
        this.oAuthClient = oAuthClient;
    }

    @Override
    public String provider() {
        return "TIKTOK";
    }

    @Override
    public CompletableFuture<SyncResult> sync(PartnerConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[TikTokAdsConnector] Starting sync job for partner connection: {}", connection != null ? connection.id() : "null");

            Map<String, String> settings = connection != null && connection.settings() != null ? connection.settings() : Map.of();
            String advertiserId = settings.getOrDefault("advertiserId", settings.getOrDefault("accountId", "7100000000000000001"));
            String refreshToken = settings.get("refreshToken");
            String accessToken = settings.get("accessToken");

            if ((accessToken == null || accessToken.isBlank()) && refreshToken != null && !refreshToken.isBlank() && oAuthClient != null) {
                try {
                    log.info("[TikTokAdsConnector] Refreshing OAuth access token via TikTokOAuthClient...");
                    AdPlatformOAuthClient.TokenResponse tokenResp = oAuthClient.refreshToken(refreshToken);
                    accessToken = tokenResp.accessToken();
                } catch (Exception e) {
                    log.error("[TikTokAdsConnector] Failed to refresh token: {}", e.getMessage());
                }
            }

            if (accessToken == null || accessToken.isBlank()) {
                accessToken = "mock_secure_tiktok_token";
            }

            try {
                String today = LocalDate.now().toString();
                List<AdPerformanceRecord> metrics = apiClient.queryCampaignPerformance(advertiserId, accessToken, today);

                int imported = metrics.size();
                int rejected = 0;
                String msg = String.format("Successfully synced %d campaigns from TikTok advertiser account [%s]", imported, advertiserId);

                log.info("[TikTokAdsConnector] Sync completed successfully: {}", msg);
                return new SyncResult(provider(), imported, rejected, msg);
            } catch (Exception e) {
                log.error("[TikTokAdsConnector] Sync failed for advertiser {}: {}", advertiserId, e.getMessage());
                return new SyncResult(provider(), 0, 1, "Sync error: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<AdPerformanceReport> queryPerformance(AdPerformanceQuery query) {
        return CompletableFuture.supplyAsync(() -> {
            String advertiserId = query != null ? query.accountId() : "7100000000000000001";
            String token = query != null && query.accessToken() != null ? query.accessToken() : "mock_token";
            String date = query != null && query.startDate() != null ? query.startDate() : LocalDate.now().toString();

            List<AdPerformanceRecord> records = apiClient.queryCampaignPerformance(advertiserId, token, date);
            return AdPerformanceReport.of(provider(), advertiserId, records);
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

            return apiClient.uploadEvents(payload.accountOrPixelId(), token, payload);
        });
    }
}
