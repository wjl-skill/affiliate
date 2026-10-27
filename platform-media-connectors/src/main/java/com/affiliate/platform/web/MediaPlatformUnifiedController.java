package com.affiliate.platform.web;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.integration.AdPerformanceQuery;
import com.affiliate.platform.integration.AdPerformanceReport;
import com.affiliate.platform.integration.AdPlatformConnector;
import com.affiliate.platform.integration.MediaPlatformConnectorRegistry;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 统一媒体侧与流量变现网络接入 REST 控制器 (Unified Media Platform REST Controller)
 * <p>
 * 为 Google Ad Manager (GAM)、TikTok Pangle、Meta Audience Network (FAN) 等变现网络提供标准解耦的接入接口：
 * 1. GET  /api/v1/media-platforms 支持的媒体变现平台列表；
 * 2. POST /api/v1/media-platforms/{network}/sync 触发媒体广告位与物料库存异步同步；
 * 3. GET  /api/v1/media-platforms/{network}/performance 查询媒体端收益与曝光指标报表。
 */
@RestController
@RequestMapping("/api/v1/media-platforms")
public class MediaPlatformUnifiedController {

    private final MediaPlatformConnectorRegistry registry;

    public MediaPlatformUnifiedController(MediaPlatformConnectorRegistry registry) {
        this.registry = registry;
    }

    /**
     * 查询当前平台支持的所有媒体与流量变现网络列表
     * GET /api/v1/media-platforms
     */
    @GetMapping
    public ResponseEntity<Set<String>> listSupportedNetworks() {
        return ResponseEntity.ok(registry.getSupportedProviders());
    }

    /**
     * 触发指定媒体平台的广告位与变现库存异步同步
     * POST /api/v1/media-platforms/{network}/sync
     */
    @PostMapping("/{network}/sync")
    public CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> triggerSync(
            @PathVariable String network,
            @RequestBody(required = false) UnifiedMediaSyncRequest request
    ) {
        AdPlatformConnector connector = registry.getConnector(network);
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";

        String targetId = (request != null && request.appId() != null && !request.appId().isBlank())
                ? request.appId()
                : (request != null && request.networkCode() != null && !request.networkCode().isBlank())
                ? request.networkCode()
                : "100200300";

        String connectionId = (request != null && request.connectionId() != null && !request.connectionId().isBlank())
                ? request.connectionId()
                : "conn-media-" + connector.provider().toLowerCase() + "-" + targetId;

        Map<String, String> settings = new HashMap<>();
        if (request != null && request.settings() != null) {
            settings.putAll(request.settings());
        }

        settings.putIfAbsent("appId", targetId);
        settings.putIfAbsent("networkCode", targetId);
        settings.putIfAbsent("fanAppId", targetId);
        settings.putIfAbsent("pangleAppId", targetId);

        String token = (request != null && request.apiToken() != null && !request.apiToken().isBlank())
                ? request.apiToken()
                : settings.getOrDefault("apiToken", "mock_token");
        settings.put("apiToken", token);
        settings.put("securityKey", token);
        settings.put("systemUserToken", token);

        PartnerConnection connection = new PartnerConnection(
                connectionId,
                tenantId,
                connector.provider() + " Supply Sync",
                SupplyType.SSP,
                "https://api." + connector.provider().toLowerCase() + ".media.com",
                settings,
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        return connector.sync(connection)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.internalServerError().body(
                        new AdPlatformConnector.SyncResult(connector.provider(), 0, 1, "Sync failed: " + ex.getMessage())
                ));
    }

    /**
     * 查询指定变现渠道的应用/媒体曝光、点击与变现预估收益报表
     * GET /api/v1/media-platforms/{network}/performance
     */
    @GetMapping("/{network}/performance")
    public CompletableFuture<ResponseEntity<AdPerformanceReport>> getPerformance(
            @PathVariable String network,
            @RequestParam(defaultValue = "media-app-1001") String accountId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "mock_token") String apiToken
    ) {
        AdPlatformConnector connector = registry.getConnector(network);
        String queryDate = (date != null && !date.isBlank()) ? date : LocalDate.now().toString();

        AdPerformanceQuery query = new AdPerformanceQuery(accountId, apiToken, queryDate);
        return connector.queryPerformance(query).thenApply(ResponseEntity::ok);
    }

    public record UnifiedMediaSyncRequest(
            String appId,
            String networkCode,
            String connectionId,
            String apiToken,
            Map<String, String> settings
    ) {}
}
