package com.affiliate.platform.integration;

import java.net.URI;
import java.time.Instant;

/**
 * 通用广告平台 OAuth 2.0 授权客户端接口契约 (Unified Ad Platform OAuth Client)
 */
public interface AdPlatformOAuthClient {

    /**
     * 对应的平台标识（如 GOOGLE_ADS, TIKTOK, FACEBOOK）
     */
    String platform();

    /**
     * 生成带租户签名的 OAuth 2.0 授权引导跳转地址
     *
     * @param tenantId 租户唯一标识
     * @return 包含目标跳转 URI 与防篡改 state 的授权请求载荷
     */
    AuthorizationRequest authorizationUrl(String tenantId);

    /**
     * 校验并单次消费 state 参数（防 CSRF 与重放）
     *
     * @param state    回调携带的 state
     * @param tenantId 预期租户标识
     * @return true 代表有效且已被成功消费清理
     */
    boolean consumeState(String state, String tenantId);

    /**
     * 使用授权码换取 Access Token 与 Refresh Token
     *
     * @param code 授权码
     * @return 令牌响应实体
     */
    TokenResponse exchangeCode(String code);

    /**
     * 使用长期 Refresh Token 刷新并申请新的有效 Access Token
     *
     * @param refreshToken 刷新凭据
     * @return 最新的令牌响应载荷
     */
    TokenResponse refreshToken(String refreshToken);

    /**
     * 授权发起响应载荷
     */
    record AuthorizationRequest(URI url, String state, Instant expiresAt) {}

    /**
     * 统一令牌响应载荷
     */
    record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType,
            String scope,
            Instant receivedAt,
            String rawJson
    ) {
        public TokenResponse(String rawJson, Instant receivedAt) {
            this(null, null, 3600L, "Bearer", null, receivedAt, rawJson);
        }

        public boolean isExpired() {
            return receivedAt.plusSeconds(expiresIn).isBefore(Instant.now().plusSeconds(60));
        }
    }
}
