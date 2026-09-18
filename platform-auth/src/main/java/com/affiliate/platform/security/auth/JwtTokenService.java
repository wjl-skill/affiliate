package com.affiliate.platform.security.auth;

import com.affiliate.platform.security.system.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 平台自签发 JWT 令牌服务 (HS256 / Nimbus JOSE)
 * <p>
 * 管理后台登录成功后签发访问令牌，载荷携带用户 ID、用户名、租户与角色集合；
 * 校验仅依赖本地共享密钥，无需外部 OAuth2 授权服务器 (issuer/JWKS)。
 */
@Service
public class JwtTokenService {

    private final byte[] signingKey;
    private final long ttlSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtTokenService(
            @Value("${app.security.jwt-secret:affiliate-platform-dev-jwt-secret-change-me-in-prod-0518}") String secret,
            @Value("${app.security.token-ttl-minutes:720}") long ttlMinutes
    ) {
        // HS256 要求 256bit 密钥：对任意长度配置口令做 SHA-256 归一化
        this.signingKey = sha256(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = Math.max(1, ttlMinutes) * 60;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String issue(UserAccount user) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.username())
                .claim("uid", user.id())
                .claim("display_name", user.displayName())
                .claim("tenant_id", user.tenantId())
                .claim("roles", user.roles() != null ? List.copyOf(user.roles()) : List.of())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(signingKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("JWT 令牌签发失败", e);
        }
    }

    public Optional<TokenPrincipal> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            SignedJWT jwt = SignedJWT.parse(token.trim());
            if (!jwt.verify(new MACVerifier(signingKey))) return Optional.empty();
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            List<String> roles = readRoles(claims.getClaim("roles"));
            return Optional.of(new TokenPrincipal(
                    (String) claims.getClaim("uid"),
                    claims.getSubject(),
                    (String) claims.getClaim("display_name"),
                    (String) claims.getClaim("tenant_id"),
                    roles,
                    claims.getExpirationTime().toInstant()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readRoles(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (raw instanceof String json) {
            try {
                return objectMapper.readValue(json, List.class);
            } catch (Exception ignored) {}
        }
        return List.of();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record TokenPrincipal(
            String userId,
            String username,
            String displayName,
            String tenantId,
            List<String> roles,
            Instant expiresAt
    ) {}
}
