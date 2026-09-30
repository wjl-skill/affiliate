package com.affiliate.platform.tenant;

import com.affiliate.platform.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 多租户与分布式全链路追踪 HTTP 请求上下文拦截过滤器 (Tenant & Trace Context Web Filter)
 * <p>
 * 继承 OncePerRequestFilter 确保每个 HTTP 请求仅执行一次。
 * 从请求头 X-Tenant-ID 中解析租户标识，从 X-Trace-Id / X-Request-Id 中提取或自生成链路追踪 ID，
 * 绑定 SLF4J MDC 上下文，并在响应头中回传 X-Trace-Id。
 * 请求结束后强制通过 finally 块清理 ThreadLocal 与 MDC，防止线程池污染。
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final java.util.regex.Pattern SAFE_TENANT_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_\\-]{1,64}$");

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
        if (tenant == null || tenant.isBlank()) {
            String headerTenant = request.getHeader("X-Tenant-ID");
            if (headerTenant != null && !headerTenant.isBlank()) {
                String trimmed = headerTenant.trim();
                tenant = SAFE_TENANT_PATTERN.matcher(trimmed).matches() ? trimmed : "public";
            } else {
                tenant = "public";
            }
            TenantContext.set(tenant);
        }
        try {
            MDC.put("tenantId", tenant);
        } catch (Throwable ignored) {
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            TraceContext.clear();
            try {
                MDC.remove("tenantId");
            } catch (Throwable ignored) {
            }
        }
    }
}
