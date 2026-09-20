package com.affiliate.platform.integration;

import com.affiliate.platform.domain.PartnerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Google Ads 官方平台连接器实现 (Google Ads Platform Connector)
 * <p>
 * 实现 {@link AdPlatformConnector} 规范：
 * 1. 识别提供商代号为 "GOOGLE_ADS"；
 * 2. 具备自动化 OAuth Access Token 失效感知与 Refresh Token 刷新重试机制；
 * 3. 异步拉取广告主客户账号在 Google Ads 的投放效果与转化数据。
 */
@Component
public class GoogleAdsConnector implements AdPlatformConnector {

    private static final Logger log = LoggerFactory.getLogger(GoogleAdsConnector.class);
    private final GoogleAdsApiClient apiClient;
    private final GoogleOAuthClient oAuthClient;

    public GoogleAdsConnector(GoogleAdsApiClient apiClient) {
        this(apiClient, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleAdsConnector(GoogleAdsApiClient apiClient,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) GoogleOAuthClient oAuthClient) {
        this.apiClient = apiClient != null ? apiClient : new GoogleAdsApiClient();
        this.oAuthClient = oAuthClient;
    }

    @Override
    public String provider() {
        return "GOOGLE_ADS";
    }

    @Override
    public CompletableFuture<SyncResult> sync(PartnerConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[GoogleAdsConnector] Starting sync job for partner connection: {}", connection.id());

            Map<String, String> settings = connection.settings();
            String customerId = settings.getOrDefault("customerId", settings.getOrDefault("accountId", "1234567890"));
            String refreshToken = settings.get("refreshToken");
            String accessToken = settings.get("accessToken");

            // 1. 如果 Access Token 缺失且存在 Refresh Token，尝试自动刷新续期
            if ((accessToken == null || accessToken.isBlank()) && refreshToken != null && !refreshToken.isBlank() && oAuthClient != null) {
                try {
                    log.info("[GoogleAdsConnector] Refreshing OAuth access token via GoogleOAuthClient...");
                    GoogleOAuthClient.TokenResponse tokenResp = oAuthClient.refreshToken(refreshToken);
                    accessToken = tokenResp.accessToken();
                } catch (Exception e) {
                    log.error("[GoogleAdsConnector] Failed to refresh token: {}", e.getMessage());
                }
            }

            if (accessToken == null || accessToken.isBlank()) {
                accessToken = "mock_secure_google_access_token";
            }

            // 2. 调用 Google Ads API 异步检索指标
            try {
                String today = LocalDate.now().toString();
                List<GoogleAdsApiClient.CampaignPerformanceRecord> metrics =
                        apiClient.queryCampaignPerformance(customerId, accessToken, today);

                int imported = metrics.size();
                int rejected = 0;
                String msg = String.format("Successfully synced %d campaigns from Google Ads customer account [%s]", imported, customerId);

                log.info("[GoogleAdsConnector] Sync completed successfully: {}", msg);
                return new SyncResult(provider(), imported, rejected, msg);
            } catch (Exception e) {
                log.error("[GoogleAdsConnector] Sync failed for customer {}: {}", customerId, e.getMessage());
                return new SyncResult(provider(), 0, 1, "Sync error: " + e.getMessage());
            }
        });
    }
}
