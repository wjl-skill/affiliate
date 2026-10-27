package com.affiliate.platform.web;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.integration.*;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 统一多广告平台接入 REST 控制器 (Unified Ad Platform REST Controller)
 * <p>
 * 提供平台无关的标准接口：
 * 1. POST /api/v1/ad-platforms/{platform}/sync 手动触发异步物料/报表同步；
 * 2. GET  /api/v1/ad-platforms/{platform}/performance 广告效果数据报表检索；
 * 3. POST /api/v1/ad-platforms/{platform}/conversions 统一 S2S/CAPI 转化事件上报；
 * 4. GET  /api/v1/ad-platforms/{platform}/oauth/authorize OAuth 授权跳转；
 * 5. GET  /api/v1/ad-platforms/{platform}/oauth/callback OAuth 授权回调。
 */
@RestController
@RequestMapping("/api/v1/ad-platforms")
public class AdPlatformUnifiedController {

    private final AdPlatformConnectorRegistry registry;

    public AdPlatformUnifiedController(AdPlatformConnectorRegistry registry) {
        this.registry = registry;
    }

    /**
     * 查询当前平台支持的所有广告平台渠道列表
     * GET /api/v1/ad-platforms
     */
    @GetMapping
    public ResponseEntity<Set<String>> listSupportedPlatforms() {
        return ResponseEntity.ok(registry.getSupportedPlatforms());
    }

    /**
     * 触发指定广告平台的广告系列与投放指标异步同步
     * POST /api/v1/ad-platforms/{platform}/sync
     */
    @PostMapping("/{platform}/sync")
    public CompletableFuture<ResponseEntity<AdPlatformConnector.SyncResult>> triggerSync(
            @PathVariable String platform,
            @RequestBody(required = false) UnifiedSyncRequest request
    ) {
        AdPlatformConnector connector = registry.getConnector(platform);
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";

        String accountId = (request != null && request.accountId() != null && !request.accountId().isBlank())
                ? request.accountId()
                : "1234567890";
        String connectionId = (request != null && request.connectionId() != null)
                ? request.connectionId()
                : "conn-" + platform.toLowerCase() + "-" + accountId;

        Map<String, String> settings = new HashMap<>();
        settings.put("accountId", accountId);
        settings.put("customerId", accountId);
        settings.put("advertiserId", accountId);
        if (request != null) {
            if (request.accessToken() != null) settings.put("accessToken", request.accessToken());
            if (request.refreshToken() != null) settings.put("refreshToken", request.refreshToken());
        }

        PartnerConnection connection = new PartnerConnection(
                connectionId,
                tenantId,
                platform.toUpperCase() + " Manual Sync",
                SupplyType.DSP,
                "https://api." + platform.toLowerCase() + ".com",
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
     * 查询指定渠道账号的投放消耗与成效报表
     * GET /api/v1/ad-platforms/{platform}/performance
     */
    @GetMapping("/{platform}/performance")
    public CompletableFuture<ResponseEntity<AdPerformanceReport>> getPerformance(
            @PathVariable String platform,
            @RequestParam(defaultValue = "123-456-7890") String accountId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "mock_token") String accessToken
    ) {
        AdPlatformConnector connector = registry.getConnector(platform);
        String queryDate = (date != null && !date.isBlank()) ? date : LocalDate.now().toString();

        AdPerformanceQuery query = new AdPerformanceQuery(accountId, accessToken, queryDate);
        return connector.queryPerformance(query).thenApply(ResponseEntity::ok);
    }

    /**
     * 统一上报 S2S / CAPI 广告转化事件 (Google Enhanced Conversions / Meta CAPI / TikTok Events API)
     * POST /api/v1/ad-platforms/{platform}/conversions
     */
    @PostMapping("/{platform}/conversions")
    public CompletableFuture<ResponseEntity<ConversionUploadResult>> uploadConversion(
            @PathVariable String platform,
            @Valid @RequestBody UnifiedConversionRequest request
    ) {
        AdPlatformConnector connector = registry.getConnector(platform);

        Instant eventTime = (request.eventTime() != null && !request.eventTime().isBlank())
                ? parseInstantSafely(request.eventTime())
                : Instant.now();

        UnifiedConversionPayload payload = new UnifiedConversionPayload(
                connector.provider(),
                request.accountOrPixelId(),
                request.accessToken() != null ? request.accessToken() : "mock_token",
                request.eventName() != null ? request.eventName() : "PURCHASE",
                request.orderId(),
                request.value(),
                request.currency() != null ? request.currency() : "USD",
                eventTime,
                request.gclid(),
                request.fbclid(),
                request.fbp(),
                request.fbc(),
                request.ttclid(),
                request.email(),
                request.phone(),
                request.clientIp(),
                request.userAgent(),
                request.customParams()
        );

        return connector.uploadConversion(payload).thenApply(ResponseEntity::ok);
    }

    /**
     * 获取指定平台的 OAuth 2.0 授权引导跳转地址
     * GET /api/v1/ad-platforms/{platform}/oauth/authorize
     */
    @GetMapping("/{platform}/oauth/authorize")
    public ResponseEntity<AdPlatformOAuthClient.AuthorizationRequest> authorize(@PathVariable String platform) {
        AdPlatformOAuthClient client = registry.getOAuthClient(platform);
        String tenant = TenantContext.required();
        var authReq = client.authorizationUrl(tenant);
        return ResponseEntity.ok(authReq);
    }

    /**
     * OAuth 2.0 授权成功后的回调端点
     * GET /api/v1/ad-platforms/{platform}/oauth/callback
     */
    @GetMapping("/{platform}/oauth/callback")
    public ResponseEntity<AdPlatformOAuthClient.TokenResponse> callback(
            @PathVariable String platform,
            @RequestParam String code,
            @RequestParam String state
    ) {
        AdPlatformOAuthClient client = registry.getOAuthClient(platform);
        String tenant = TenantContext.required();
        if (!client.consumeState(state, tenant)) {
            throw new IllegalArgumentException("invalid or expired OAuth state for platform: " + platform);
        }
        var token = client.exchangeCode(code);
        return ResponseEntity.ok(new AdPlatformOAuthClient.TokenResponse(
                "stored-server-side", null, token.expiresIn(), token.tokenType(), token.scope(), token.receivedAt(), null
        ));
    }

    private Instant parseInstantSafely(String str) {
        try {
            return Instant.parse(str);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    public record UnifiedSyncRequest(
            String accountId,
            String connectionId,
            String accessToken,
            String refreshToken
    ) {}

    public record UnifiedConversionRequest(
            @NotBlank String accountOrPixelId,
            String accessToken,
            String eventName,
            @NotBlank String orderId,
            @NotNull BigDecimal value,
            String currency,
            String eventTime,
            String gclid,
            String fbclid,
            String fbp,
            String fbc,
            String ttclid,
            String email,
            String phone,
            String clientIp,
            String userAgent,
            Map<String, Object> customParams
    ) {}
}
