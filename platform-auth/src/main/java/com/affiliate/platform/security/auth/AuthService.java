package com.affiliate.platform.security.auth;

import com.affiliate.platform.security.system.UserAccount;
import com.affiliate.platform.security.system.UserAccountService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * 管理后台登录认证服务 (Console Authentication Service)
 * <p>
 * 校验 BCrypt 口令并签发 JWT 访问令牌；账号非 ACTIVE 状态直接拒绝登录。
 */
@Service
public class AuthService {

    private final UserAccountService userAccountService;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserAccountService userAccountService, JwtTokenService jwtTokenService) {
        this.userAccountService = userAccountService;
        this.jwtTokenService = jwtTokenService;
    }

    public record LoginResult(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserAccount user
    ) {}

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
        return new LoginResult(
                jwtTokenService.issue(loggedIn),
                "Bearer",
                jwtTokenService.getTtlSeconds(),
                loggedIn
        );
    }

    /**
     * 按令牌主体加载最新用户档案 (角色/状态可能已变更)
     */
    public UserAccount loadPrincipalUser(JwtTokenService.TokenPrincipal principal) {
        return userAccountService.find(principal.userId())
                .orElseGet(() -> userAccountService.findByUsername(principal.username())
                        .orElseThrow(() -> new NoSuchElementException("用户不存在: " + principal.username())));
    }

    /**
     * 登录凭证校验失败异常 (统一映射为 HTTP 401)
     */
    public static class BadLoginException extends RuntimeException {
        public BadLoginException(String message) {
            super(message);
        }
    }
}
