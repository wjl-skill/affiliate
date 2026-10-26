package com.affiliate.platform.security.auth;

import com.affiliate.platform.security.system.UserAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台鉴权接口 (Console Auth REST Controller)
 * <p>
 * 提供登录签发 JWT、令牌轮换换票 (Refresh Token Rotation)、查询当前登录用户与安全登出主动吊销。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthService authService, JwtTokenService jwtTokenService) {
        this.authService = authService;
        this.jwtTokenService = jwtTokenService;
    }

    public record LoginRequest(String username, String password) {}
    public record RefreshRequest(String refreshToken) {}
    public record LogoutRequest(String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthService.LoginResult result = authService.login(request.username(), request.password());
            return ResponseEntity.ok(result);
        } catch (AuthService.BadLoginException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 无感刷新令牌端点：使用 Refresh Token 单次轮换新访问令牌与新刷新令牌
     * POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            AuthService.LoginResult result = authService.refresh(request.refreshToken());
            return ResponseEntity.ok(result);
        } catch (AuthService.BadLoginException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        String token = extractBearer(authorization);
        return jwtTokenService.parse(token)
                .map(principal -> {
                    UserAccount user = authService.loadPrincipalUser(principal);
                    if (user.status() != UserAccount.Status.ACTIVE) {
                        return ResponseEntity.status(401).body(Map.of("message", "账号已失效，请重新登录"));
                    }
                    return ResponseEntity.<Object>ok(Map.of(
                            "token", Map.of(
                                    "userId", user.id(),
                                    "username", user.username(),
                                    "tenantId", user.tenantId() != null ? user.tenantId() : "public",
                                    "roles", user.roles(),
                                    "expiresAt", principal.expiresAt().toString(),
                                    "jti", principal.jti() != null ? principal.jti() : ""
                            ),
                            "user", user
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("message", "未登录或访问令牌已失效")));
    }

    /**
     * 安全注销：服务端主动吊销当前 Access Token (加入 JTI 黑名单) 并注销对应 Refresh Token
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody(required = false) LogoutRequest body
    ) {
        String accessToken = extractBearer(authorization);
        String refreshToken = body != null ? body.refreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }

    private static String extractBearer(String authorization) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7);
        }
        return null;
    }
}
