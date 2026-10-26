package com.affiliate.platform.security.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 刷新令牌单次使用轮换与重放检测服务 (Refresh Token Rotation & Replay Detection Service)
 * <p>
 * 遵循 OAuth2 安全最佳实践：
 * 1. 签发高熵安全 Refresh Token，有效期默认 7 天；
 * 2. 绑定令牌族 (Family ID)：每次刷新时轮换新 Token，旧 Token 立即作废；
 * 3. 重放检测 (Replay Attack Detection)：若已作废的 Refresh Token 再次被请求，
 *    判定为凭据泄露拦截攻击，立即吊销该令牌族所有派生令牌并强制下线。
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long REFRESH_TTL_SECONDS = 7 * 24 * 3600; // 7 天

    public record RefreshRecord(
            String token,
            String userId,
            String username,
            String tenantId,
            String familyId,
            Instant expiresAt,
            boolean used
    ) {}

    // 本地缓存维护，支持多实例通过共享分布式存储扩展
    private final ConcurrentMap<String, RefreshRecord> tokenStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> familyTokens = new ConcurrentHashMap<>();

    /**
     * 为新登录用户创建首个 Refresh Token
     */
    public String createRefreshToken(String userId, String username, String tenantId) {
        String token = generateSecureToken();
        String familyId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(REFRESH_TTL_SECONDS);

        RefreshRecord record = new RefreshRecord(token, userId, username, tenantId, familyId, expiresAt, false);
        tokenStore.put(token, record);
        familyTokens.computeIfAbsent(familyId, k -> ConcurrentHashMap.newKeySet()).add(token);
        return token;
    }

    /**
     * 单次使用轮换 (Token Rotation)：校验旧令牌并签发新刷新令牌
     */
    public synchronized Optional<RefreshRecord> rotate(String oldToken) {
        if (oldToken == null || oldToken.isBlank()) {
            return Optional.empty();
        }

        RefreshRecord existing = tokenStore.get(oldToken.trim());
        if (existing == null) {
            log.warn("[RefreshToken] 尝试使用未知的 Refresh Token 进行换票");
            return Optional.empty();
        }

        if (existing.expiresAt().isBefore(Instant.now())) {
            log.warn("[RefreshToken] Refresh Token 已过期: 用户={}", existing.username());
            tokenStore.remove(oldToken);
            return Optional.empty();
        }

        // 重放检测：已被使用的旧 Refresh Token 再次出现
        if (existing.used()) {
            log.error("[CRITICAL SECURITY] 检测到 Refresh Token 重放攻击！立即销毁整个令牌族: familyId={}, 用户={}",
                    existing.familyId(), existing.username());
            revokeFamily(existing.familyId());
            return Optional.empty();
        }

        // 标记旧 Token 为已使用
        tokenStore.put(oldToken, new RefreshRecord(
                existing.token(),
                existing.userId(),
                existing.username(),
                existing.tenantId(),
                existing.familyId(),
                existing.expiresAt(),
                true
        ));

        // 签发同族系的新 Refresh Token
        String newToken = generateSecureToken();
        Instant newExpiresAt = Instant.now().plusSeconds(REFRESH_TTL_SECONDS);
        RefreshRecord rotated = new RefreshRecord(
                newToken,
                existing.userId(),
                existing.username(),
                existing.tenantId(),
                existing.familyId(),
                newExpiresAt,
                false
        );

        tokenStore.put(newToken, rotated);
        Set<String> family = familyTokens.get(existing.familyId());
        if (family != null) {
            family.add(newToken);
        }

        return Optional.of(rotated);
    }

    /**
     * 吊销整个令牌族
     */
    public void revokeFamily(String familyId) {
        if (familyId == null) return;
        Set<String> tokens = familyTokens.remove(familyId);
        if (tokens != null) {
            for (String t : tokens) {
                tokenStore.remove(t);
            }
        }
    }

    /**
     * 吊销单个 Refresh Token
     */
    public void revokeToken(String token) {
        if (token != null) {
            RefreshRecord r = tokenStore.remove(token.trim());
            if (r != null) {
                Set<String> f = familyTokens.get(r.familyId());
                if (f != null) {
                    f.remove(token);
                }
            }
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
