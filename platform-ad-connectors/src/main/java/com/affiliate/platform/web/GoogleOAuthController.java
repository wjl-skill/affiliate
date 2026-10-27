package com.affiliate.platform.web;

import com.affiliate.platform.integration.GoogleOAuthClient;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

/**
 * Google Ads / GAM OAuth 授权 REST 控制器 (Google OAuth REST Controller - 向后兼容层)
 */
@RestController
@RequestMapping("/api/v1/google/oauth")
@ConditionalOnBean(GoogleOAuthClient.class)
public class GoogleOAuthController {

    private final GoogleOAuthClient client;

    public GoogleOAuthController(GoogleOAuthClient client) {
        this.client = client;
    }

    /**
     * 发起 Google 授权认证，获取登录跳转 URL
     * GET /api/v1/google/oauth/authorize
     */
    @GetMapping("/authorize")
    public AuthorizationResponse authorize() {
        var request = client.authorizationUrl(TenantContext.required());
        return new AuthorizationResponse(request.url(), request.state(), request.expiresAt());
    }

    /**
     * Google OAuth 授权成功后的重定向回调端点
     * GET /api/v1/google/oauth/callback?code=xxx&state=yyy
     */
    @GetMapping("/callback")
    public GoogleOAuthClient.TokenResponse callback(@RequestParam String code, @RequestParam String state) {
        String tenant = TenantContext.required();
        if (!client.consumeState(state, tenant)) {
            throw new IllegalArgumentException("invalid or expired OAuth state");
        }
        GoogleOAuthClient.TokenResponse token = client.exchangeGoogleCode(code);
        return new GoogleOAuthClient.TokenResponse("stored-server-side", token.receivedAt());
    }

    public record AuthorizationResponse(URI url, String state, Instant expiresAt) {}
}
