package com.affiliate.platform.security.auth;

import com.affiliate.platform.security.system.UserAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台鉴权接口 (Console Auth REST Controller)
 * <p>
 * 提供登录签发 JWT、查询当前登录用户与登出 (无状态，令牌由前端清除) 三个端点。
 * 其中 /login 为匿名放行端点，其余依赖 Bearer 令牌。
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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthService.LoginResult result = authService.login(request.username(), request.password());
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
                                    "expiresAt", principal.expiresAt().toString()
                            ),
                            "user", user
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("message", "未登录或访问令牌已失效")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // 无状态 JWT：服务端不保留会话，客户端销毁令牌即完成登出
        return ResponseEntity.noContent().build();
    }

    private static String extractBearer(String authorization) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7);
        }
        return null;
    }
}
