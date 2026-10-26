package com.affiliate.platform.security.auth;

import com.affiliate.platform.security.system.UserAccount;
import com.affiliate.platform.security.system.UserAccountService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * 管理后台登录与令牌生命周期服务 (Console Authentication Service)
 * <p>
 * 支持 BCrypt 校验、JWT 签发、Refresh Token 单次轮换及黑名单主动吊销。
 */
@Service
public class AuthService {

    private final UserAccountService userAccountService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserAccountService userAccountService,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService
    ) {
        this.userAccountService = userAccountService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    public record LoginResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserAccount user
    ) {
        public LoginResult(String accessToken, String tokenType, long expiresIn, UserAccount user) {
            this(accessToken, null, tokenType, expiresIn, user);
        }
    }

    public LoginResult login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BadLoginException("请输入用户名与密码");
        }
        UserAccount user = userAccountService.findByUsername(username.trim())
                .orElseThrow(() -> new BadLoginException("用户名或密码错误"));
        if (user.status() != UserAccount.Status.ACTIVE) {
            throw new BadLoginException("账号已被冻结或锁定，请联系超级管理员");
        }
        if (!userAccountService.matchesPassword(user.username(), rawPassword)) {
            throw new BadLoginException("用户名或密码错误");
        }

        userAccountService.recordLogin(user.id());
        UserAccount loggedIn = user.withLastLogin(java.time.Instant.now());

        String accessToken = jwtTokenService.issue(loggedIn);
        String refreshToken = refreshTokenService.createRefreshToken(user.id(), user.username(), user.tenantId());

        return new LoginResult(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenService.getTtlSeconds(),
                loggedIn
        );
    }

    /**
     * 使用 Refresh Token 换发新访问令牌与新刷新令牌（单次使用轮换）
     */
    public LoginResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadLoginException("无效的刷新令牌");
        }

        RefreshTokenService.RefreshRecord record = refreshTokenService.rotate(refreshToken)
                .orElseThrow(() -> new BadLoginException("刷新令牌已失效或重放异常，请重新登录"));

        UserAccount user = userAccountService.find(record.userId())
                .orElseGet(() -> userAccountService.findByUsername(record.username())
                        .orElseThrow(() -> new BadLoginException("用户账户不存在")));

        if (user.status() != UserAccount.Status.ACTIVE) {
            throw new BadLoginException("账户已被冻结，禁止换发令牌");
        }

        String newAccessToken = jwtTokenService.issue(user);
        return new LoginResult(
                newAccessToken,
                record.token(),
                "Bearer",
                jwtTokenService.getTtlSeconds(),
                user
        );
    }

    /**
     * 登出并主动吊销访问令牌与刷新令牌
     */
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            jwtTokenService.parse(accessToken).ifPresent(principal -> {
                if (principal.jti() != null) {
                    jwtTokenService.revokeJti(principal.jti(), principal.expiresAt());
                }
            });
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }
    }

    /**
     * 按令牌主体加载最新用户档案
     */
    public UserAccount loadPrincipalUser(JwtTokenService.TokenPrincipal principal) {
        return userAccountService.find(principal.userId())
                .orElseGet(() -> userAccountService.findByUsername(principal.username())
                        .orElseThrow(() -> new NoSuchElementException("用户不存在: " + principal.username())));
    }

    public static class BadLoginException extends RuntimeException {
        public BadLoginException(String message) {
            super(message);
        }
    }
}
