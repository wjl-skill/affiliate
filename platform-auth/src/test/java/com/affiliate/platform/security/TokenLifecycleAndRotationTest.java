package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.AuthService;
import com.affiliate.platform.security.auth.JwtTokenService;
import com.affiliate.platform.security.auth.RefreshTokenService;
import com.affiliate.platform.security.system.UserAccount;
import com.affiliate.platform.security.system.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TokenLifecycleAndRotationTest {

    private JwtTokenService jwtTokenService;
    private RefreshTokenService refreshTokenService;
    private UserAccountService userAccountService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService("c2VjdXJlLXByb2R1Y3Rpb24tZmluYW5jaWFsLWdyYWRlLTItc2VjcmV0", 60);
        refreshTokenService = new RefreshTokenService();
        userAccountService = mock(UserAccountService.class);
        authService = new AuthService(userAccountService, jwtTokenService, refreshTokenService);
    }

    private UserAccount createSampleUser() {
        return new UserAccount(
                "u-1", "tenant-1", "john_doe", "John Doe",
                "john@example.com", "13800000000", null,
                UserAccount.Status.ACTIVE, Set.of("FINANCE"),
                Instant.now(), Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("JWT 签发并包含唯一 JTI，主动吊销后再次验证必须被拒绝")
    void testJtiRevocation() {
        UserAccount user = createSampleUser();

        String token = jwtTokenService.issue(user);
        Optional<JwtTokenService.TokenPrincipal> parsed = jwtTokenService.parse(token);

        assertThat(parsed).isPresent();
        String jti = parsed.get().jti();
        assertThat(jti).isNotBlank();

        // 主动将 JTI 吊销加入黑名单
        jwtTokenService.revokeJti(jti, parsed.get().expiresAt());

        // 再次解析该有效签名的 Token，必须返回 empty（校验失败）
        Optional<JwtTokenService.TokenPrincipal> revokedCheck = jwtTokenService.parse(token);
        assertThat(revokedCheck).isEmpty();
    }

    @Test
    @DisplayName("Refresh Token 正常轮换：签发新令牌并作废旧令牌")
    void testRefreshTokenRotationNormal() {
        UserAccount user = createSampleUser();
        when(userAccountService.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(userAccountService.matchesPassword("john_doe", "password123")).thenReturn(true);
        when(userAccountService.find("u-1")).thenReturn(Optional.of(user));

        AuthService.LoginResult loginResult = authService.login("john_doe", "password123");
        String oldRefreshToken = loginResult.refreshToken();
        assertThat(oldRefreshToken).isNotBlank();

        // 正常轮换换票
        AuthService.LoginResult refreshed = authService.refresh(oldRefreshToken);
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(oldRefreshToken);
    }

    @Test
    @DisplayName("Refresh Token 重放检测：已被使用的旧令牌再次请求，必须触发安全熔断并销毁整个族系")
    void testRefreshTokenReplayAttackDetection() {
        UserAccount user = createSampleUser();
        when(userAccountService.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(userAccountService.matchesPassword("john_doe", "password123")).thenReturn(true);
        when(userAccountService.find("u-1")).thenReturn(Optional.of(user));

        AuthService.LoginResult loginResult = authService.login("john_doe", "password123");
        String stolenOldToken = loginResult.refreshToken();

        // 首次合法客户端正常换发
        AuthService.LoginResult refreshed1 = authService.refresh(stolenOldToken);
        String legitimateNewToken = refreshed1.refreshToken();

        // 黑客/攻击者尝试使用已失效的旧 Token 再次换发 -> 触发重放检测
        assertThatThrownBy(() -> authService.refresh(stolenOldToken))
                .isInstanceOf(AuthService.BadLoginException.class)
                .hasMessageContaining("重放异常");

        // 攻击触发后，整个族系都被销毁，原本合法的最新 Token 也随之失效阻断，保护账户
        assertThatThrownBy(() -> authService.refresh(legitimateNewToken))
                .isInstanceOf(AuthService.BadLoginException.class);
    }
}
