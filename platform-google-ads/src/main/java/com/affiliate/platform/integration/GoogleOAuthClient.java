package com.affiliate.platform.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Ads / GAM OAuth 2.0 授权客户端实现 (Google OAuth 2.0 Client)
 * <p>
 * 遵循 Google 官方 OAuth 2.0 Web Server 授权码流标准：
 * 1. 构建防 CSRF 攻击的租户级 state 参数与授权重定向跳转 URL；
 * 2. 状态值单次消费 (Consume State) 校验防重放；
 * 3. 授权码 (Authorization Code) 换取 Access Token 与 Refresh Token。
 * 仅在配置 `app.google.enabled=true` 时激活装配。
 */
@Component
@ConditionalOnProperty(name = "app.google.enabled", havingValue = "true")
public class GoogleOAuthClient {

    // Google OAuth 客户端 ID
    private final String id;

    // Google OAuth 客户端密钥
    private final String secret;

    // OAuth 授权成功后的回调重定向 URL
    private final String redirect;

    // 申请的 Google API 权限范围列表
    private final List<String> scopes;

    // 内存 State 校验表：Key 为 state 字符串，Value 为其过期时间戳（5分钟有效期）
    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    public GoogleOAuthClient(
            @Value("${app.google.client-id}") String id,
            @Value("${app.google.client-secret}") String secret,
            @Value("${app.google.redirect-uri}") String redirect,
            @Value("#{'${app.google.scopes}'.split(',')}") List<String> scopes
    ) {
        this.id = id;
        this.secret = secret;
        this.redirect = redirect;
        this.scopes = scopes;
    }

    /**
     * 生成带租户签名的 Google OAuth 2.0 授权引导跳转地址
     *
     * @param tenant 租户唯一标识
     * @return 包含目标跳转 URI 与防篡改 state 的授权请求载荷
     */
    public AuthorizationRequest authorizationUrl(String tenant) {
        // 生成 state 参数：格式为 "tenant.UUID"，并设置 300 秒（5分钟）有效期
        String state = tenant + "." + UUID.randomUUID();
        states.put(state, Instant.now().plusSeconds(300));

        // 构建 Google 授权跳转 URL
        URI u = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", id)
                .queryParam("redirect_uri", redirect)
                .queryParam("response_type", "code")
                .queryParam("access_type", "offline") // 请求 offline 以便获取长期 Refresh Token
                .queryParam("prompt", "consent")
                .queryParam("scope", String.join(" ", scopes))
                .queryParam("state", state)
                .build()
                .toUri();

        return new AuthorizationRequest(u, state, states.get(state));
    }

    /**
     * 校验并单次消费 state 参数（防 CSRF 与重放）
     *
     * @param state  回调携带的 state
     * @param tenant 预期租户标识
     * @return true 代表有效且已被成功消费清理
     */
    public boolean consumeState(String state, String tenant) {
        Instant expiresAt = states.remove(state);
        return expiresAt != null && expiresAt.isAfter(Instant.now()) && state.startsWith(tenant + ".");
    }

    /**
     * 使用回调中的授权码 code 向 Google 服务器换取 Token
     *
     * @param code 授权码
     * @return Google 返回的原始 Token 响应
     */
    /**
     * 使用授权码换取 Token 响应
     *
     * @param code 授权码
     * @return Google 返回的 Token 响应
     */
    public TokenResponse exchangeCode(String code) {
        String body = "code=" + enc(code) +
                "&client_id=" + enc(id) +
                "&client_secret=" + enc(secret) +
                "&redirect_uri=" + enc(redirect) +
                "&grant_type=authorization_code";
        return executeTokenRequest(body);
    }

    /**
     * 使用长期 Refresh Token 向 Google 重新申请有效 Access Token
     *
     * @param refreshToken 刷新凭据
     * @return 最新的 Token 响应载荷
     */
    public TokenResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token must not be null or blank");
        }
        String body = "refresh_token=" + enc(refreshToken) +
                "&client_id=" + enc(id) +
                "&client_secret=" + enc(secret) +
                "&grant_type=refresh_token";
        return executeTokenRequest(body);
    }

    private TokenResponse executeTokenRequest(String formBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Google OAuth request failed with HTTP status " + response.statusCode() + ": " + response.body());
            }

            return TokenResponse.of(response.body(), Instant.now());
        } catch (Exception e) {
            throw new IllegalStateException("Google OAuth token request failed", e);
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /**
     * 授权发起请求体
     */
    public record AuthorizationRequest(URI url, String state, Instant expiresAt) {}

    /**
     * Token 响应体（支持原始 JSON 与结构化字段读取）
     */
    public record TokenResponse(
            String rawJson,
            Instant receivedAt,
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType,
            String scope
    ) {
        public TokenResponse(String rawJson, Instant receivedAt) {
            this(rawJson, receivedAt, null, null, 3600, "Bearer", null);
        }

        public boolean isExpired() {
            return receivedAt.plusSeconds(expiresIn).isBefore(Instant.now().plusSeconds(60));
        }

        public static TokenResponse of(String rawJson, Instant receivedAt) {
            String access = extractJsonString(rawJson, "access_token");
            String refresh = extractJsonString(rawJson, "refresh_token");
            long expires = extractJsonLong(rawJson, "expires_in", 3600);
            String type = extractJsonString(rawJson, "token_type");
            String scope = extractJsonString(rawJson, "scope");
            return new TokenResponse(rawJson, receivedAt, access, refresh, expires, type != null ? type : "Bearer", scope);
        }

        private static String extractJsonString(String json, String key) {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            return m.find() ? m.group(1) : null;
        }

        private static long extractJsonLong(String json, String key, long defaultValue) {
            String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            return m.find() ? Long.parseLong(m.group(1)) : defaultValue;
        }
    }
}
