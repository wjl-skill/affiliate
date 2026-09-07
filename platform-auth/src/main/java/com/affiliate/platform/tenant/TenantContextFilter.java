package com.affiliate.platform.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 多租户 HTTP 请求上下文拦截过滤器 (Tenant Context Web Filter)
 * <p>
 * 继承 OncePerRequestFilter 确保每个 HTTP 请求仅执行一次。
 * 从请求头 X-Tenant-ID 中解析租户标识，并在请求结束后强制通过 finally 块清理 ThreadLocal，防止线程池污染。
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 1. 若安全链（如 JWT 解析器）已先行绑定租户，严禁客户端请求头恶意覆盖
        String tenant = TenantContext.get();
        if (tenant == null || tenant.isBlank()) {
            // 2. 从 HTTP 请求头中提取 "X-Tenant-ID"
            tenant = request.getHeader("X-Tenant-ID");
            // 3. 若未传租户头则默认降级为 "public" 公共租户空间
            TenantContext.set(tenant == null || tenant.isBlank() ? "public" : tenant.trim());
        }

        try {
            // 4. 继续执行后续过滤器链与 Controller 业务方法
            chain.doFilter(request, response);
        } finally {
            // 5. 请求处理完毕，绝对在 finally 中彻底清除线程上下文
            TenantContext.clear();
        }
    }
}
