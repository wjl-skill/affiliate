package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.JwtTokenService;
import com.affiliate.platform.tenant.TenantContext;
import com.affiliate.platform.tenant.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantContextFilterSecurityTest {

    private TenantContextFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new TenantContextFilter();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("普通用户携带有效令牌请求自身租户，必须顺利放行")
    void testNormalTenantAccessPasses() throws ServletException, IOException {
        JwtTokenService.TokenPrincipal principal = new JwtTokenService.TokenPrincipal(
                "u-1", "alice", "Alice", "tenant-alpha",
                List.of("TRAFFICKER"), Instant.now().plusSeconds(3600)
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TRAFFICKER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-alpha");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        // 执行后 ThreadLocal 必须被清理干净
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("普通租户用户试图通过伪造 X-Tenant-ID 请求头越权访问其他租户，必须返回 403 阻断")
    void testTamperedTenantHeaderBlocked() throws ServletException, IOException {
        JwtTokenService.TokenPrincipal principal = new JwtTokenService.TokenPrincipal(
                "u-1", "alice", "Alice", "tenant-alpha",
                List.of("TRAFFICKER"), Instant.now().plusSeconds(3600)
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TRAFFICKER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        // 伪造请求头，企图跨越到 tenant-beta
        request.addHeader("X-Tenant-ID", "tenant-beta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        // 验证禁止调用下游 FilterChain
        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("非法跨租户请求");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("平台超级管理员 (SUPER_ADMIN) 具备跨租户运维权限，允许指定目标 X-Tenant-ID")
    void testSuperAdminCrossTenantAllowed() throws ServletException, IOException {
        JwtTokenService.TokenPrincipal principal = new JwtTokenService.TokenPrincipal(
                "u-super", "admin", "Admin", "master-tenant",
                List.of("SUPER_ADMIN"), Instant.now().plusSeconds(3600)
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-client-99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            // 在 Filter 链内部验证租户被合法切换为目标租户
            assertThat(TenantContext.get()).isEqualTo("tenant-client-99");
        });

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(TenantContext.get()).isNull();
    }
}
