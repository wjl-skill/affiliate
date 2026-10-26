package com.affiliate.platform.security.auth;

import com.affiliate.platform.security.system.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 平台自签发 JWT 令牌服务 (HS256 / Nimbus JOSE)
 * <p>
 * 遵循阶段 0-2 认证安全规范：
 * 1. 签发时注入高熵唯一 jti (JWT ID)，支持精准吊销与黑名单机制；
 * 2. 载荷携带用户 ID、用户名、租户、角色集合与失效时间；
 * 3. 校验时同步检查签名、有效期与主动吊销黑名单。
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final byte[] signingKey;
    private final long ttlSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内存态 JTI 吊销黑名单 (jti -> expiresAt)，支持分布式缓存扩展
    private final ConcurrentMap<String, Instant> revokedJtiStore = new ConcurrentHashMap<>();

    public static final String DEFAULT_DEV_SECRET = "affiliate-platform-dev-jwt-secret-change-me-in-prod-0518";

    @org.springframework.beans.factory.annotation.Autowired
    public JwtTokenService(
            @Value("${app.security.jwt-secret:affiliate-platform-dev-jwt-secret-change-me-in-prod-0518}") String secret,
            @Value("${app.security.token-ttl-minutes:720}") long ttlMinutes,
            @Value("${spring.profiles.active:dev}") String activeProfile
    ) {
        if (activeProfile != null && (activeProfile.contains("prod") || activeProfile.contains("production"))) {
            if (secret == null || secret.isBlank() || DEFAULT_DEV_SECRET.equals(secret.trim()) || secret.length() < 32) {
                throw new IllegalStateException("【生产安全阻断】生产环境必须通过环境变量 APP_JWT_SECRET 提供高强度密钥（长度不得少于32位且严禁使用公开默认值）");
            }
        }
        String effectiveSecret = (secret != null && !secret.isBlank()) ? secret : DEFAULT_DEV_SECRET;
        this.signingKey = sha256(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = Math.max(1, ttlMinutes) * 60;
    }

    public JwtTokenService(String secret, long ttlMinutes) {
        this(secret, ttlMinutes, "dev");
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String issue(UserAccount user) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString().replace("-", "");
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(jti)
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
            log.error("Failed to sign JWT token for user {}: {}", user.username(), e.getMessage(), e);
            throw new IllegalStateException("JWT 令牌签发失败", e);
        }
    }

    public Optional<TokenPrincipal> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            SignedJWT jwt = SignedJWT.parse(token.trim());
            if (!jwt.verify(new MACVerifier(signingKey))) return Optional.empty();
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant expiresAt = claims.getExpirationTime() != null ? claims.getExpirationTime().toInstant() : null;
            if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
                return Optional.empty();
            }

            // 检查 JTI 是否在主动吊销黑名单中
            String jti = claims.getJWTID();
            if (jti != null && isJtiRevoked(jti)) {
                log.debug("[JwtTokenService] Token 的 JTI '{}' 已被主动吊销，拒绝访问", jti);
                return Optional.empty();
            }

            List<String> roles = readRoles(claims.getClaim("roles"));
            return Optional.of(new TokenPrincipal(
                    (String) claims.getClaim("uid"),
                    claims.getSubject(),
                    (String) claims.getClaim("display_name"),
                    (String) claims.getClaim("tenant_id"),
                    roles,
                    expiresAt,
                    jti
            ));
        } catch (Exception e) {
            log.debug("JWT parse/verify failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 将目标 Token 的 JTI 登记至主动吊销黑名单
     */
    public void revokeJti(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) return;
        Instant expiry = expiresAt != null ? expiresAt : Instant.now().plusSeconds(ttlSeconds);
        revokedJtiStore.put(jti.trim(), expiry);
        cleanupExpiredBlacklist();
    }

    public boolean isJtiRevoked(String jti) {
        if (jti == null) return false;
        Instant expiry = revokedJtiStore.get(jti);
        if (expiry == null) return false;
        if (expiry.isBefore(Instant.now())) {
            revokedJtiStore.remove(jti);
            return false;
        }
        return true;
    }

    private void cleanupExpiredBlacklist() {
        if (revokedJtiStore.size() > 2000) {
            Instant now = Instant.now();
            revokedJtiStore.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
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
            } catch (Exception e) {
                log.debug("Failed to deserialize JWT roles json: {}", e.getMessage());
            }
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
            Instant expiresAt,
            String jti
    ) {
        public TokenPrincipal(String userId, String username, String displayName, String tenantId, List<String> roles, Instant expiresAt) {
            this(userId, username, displayName, tenantId, roles, expiresAt, null);
        }
    }
}
