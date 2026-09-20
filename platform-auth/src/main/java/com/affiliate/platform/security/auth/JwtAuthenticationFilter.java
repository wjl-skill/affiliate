package com.affiliate.platform.security.auth;

import com.affiliate.platform.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer JWT 认证过滤器 (Jwt Authentication Filter)
 * <p>
 * 解析 Authorization: Bearer 令牌，验签与有效期通过后写入 SecurityContext 主体与角色权限；
 * 未携带或非法令牌不直接拒绝，由安全过滤链的授权规则统一返回 401。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenService.parse(header.substring(7)).ifPresent(principal -> {
                List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

                // 权威数字签名验签成功：从 JWT Claim 中提取真实租户 ID 重新绑定 TenantContext，覆盖客户端请求头伪造租户
                if (principal.tenantId() != null && !principal.tenantId().isBlank()) {
                    TenantContext.set(principal.tenantId().trim());
                }
            });
        }
        chain.doFilter(request, response);
    }
}
