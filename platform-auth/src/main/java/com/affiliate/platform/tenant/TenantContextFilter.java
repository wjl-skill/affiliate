package com.affiliate.platform.tenant;

import com.affiliate.platform.security.auth.JwtTokenService;
import com.affiliate.platform.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 多租户与分布式全链路追踪 HTTP 请求上下文拦截过滤器 (Tenant & Trace Context Web Filter)
 * <p>
 * 遵循商业级多租户纵深防御准则：
 * 1. 优先以已验签 JWT 令牌中的 tenant_id claim 作为权威事实源；
 * 2. 针对客户端伪造 X-Tenant-ID 请求头进行越权校验：非超级管理员严禁跨租户篡改；
 * 3. 提取或自动生成分布式追踪 TraceId 并挂载 MDC；
 * 4. 请求结束通过 finally 强制清理，防止线程复用污染。
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private static final Pattern SAFE_TENANT_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_\\-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 1. 提取或初始化分布式追踪 ID
        String traceId = request.getHeader(TraceContext.TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(TraceContext.REQUEST_HEADER);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        TraceContext.set(traceId);
        response.setHeader(TraceContext.TRACE_HEADER, traceId);

        // 2. 解析多租户上下文
        String tenant = TenantContext.get();
        String headerTenant = request.getHeader("X-Tenant-ID");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof JwtTokenService.TokenPrincipal principal) {
            String tokenTenant = principal.tenantId();
            boolean isSuperAdmin = principal.roles() != null && principal.roles().contains("SUPER_ADMIN");

            // 若请求头携带了 X-Tenant-ID 且与令牌 tenant 不一致：仅平台超级管理员允许指定目标租户
            if (headerTenant != null && !headerTenant.isBlank() && !headerTenant.equalsIgnoreCase(tokenTenant)) {
                if (!isSuperAdmin) {
                    log.warn("[TenantContextFilter] 拦截到潜在跨租户越权请求: 用户={}, 令牌租户={}, 伪造头租户={}",
                            principal.username(), tokenTenant, headerTenant);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"非法跨租户请求：无权访问目标租户资源\"}");
                    return;
                }
                tenant = headerTenant.trim();
            } else {
                tenant = tokenTenant;
            }
        } else if (tenant == null || tenant.isBlank()) {
            // 未携带身份认证或公共端点（如 RTB / 网盟回调）
            if (headerTenant != null && !headerTenant.isBlank()) {
                String trimmed = headerTenant.trim();
                tenant = SAFE_TENANT_PATTERN.matcher(trimmed).matches() ? trimmed : "public";
            } else {
                tenant = "public";
            }
        }

        TenantContext.set(tenant);
        try {
            MDC.put("tenantId", tenant);
            MDC.put("traceId", traceId);
        } catch (Throwable t) {
            log.trace("Failed to put tenantId/traceId into MDC: {}", t.getMessage());
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            TraceContext.clear();
            try {
                MDC.remove("tenantId");
                MDC.remove("traceId");
            } catch (Throwable t) {
                log.trace("Failed to remove tenantId/traceId from MDC: {}", t.getMessage());
            }
        }
    }
}
