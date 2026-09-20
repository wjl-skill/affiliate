package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.JwtAuthenticationFilter;
import com.affiliate.platform.security.auth.JwtTokenService;
import com.affiliate.platform.security.system.UserAccount;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTenantContextBindingTest {

    private JwtTokenService jwtTokenService;
    private JwtAuthenticationFilter jwtFilter;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService("test-secret-key-that-is-sufficiently-long-and-secure-12345", 60);
        jwtFilter = new JwtAuthenticationFilter(jwtTokenService);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("(d) 租户重新绑定测试：从有效 JWT Claim 提取 tenant_id 并覆盖伪造的 X-Tenant-ID")
    void testJwtClaimRebindsTenantContextAndOverridesForgedHeader() throws ServletException, IOException {
        // 1. 模拟签发一个带有真实租户信息的有效访问令牌
        UserAccount user = new UserAccount(
                "usr-test-100", "tenant-enterprise-real-001", "verified_user", "Verified User",
                "verified@affiliate.io", "+1-888-555-0100", null,
                UserAccount.Status.ACTIVE, java.util.Set.of("AFFILIATE_MANAGER"),
                null, java.time.Instant.now(), java.time.Instant.now()
        );
        String token = jwtTokenService.issue(user);
        assertNotNull(token);

        // 2. 模拟客户端发起请求，携带正确的 Bearer Token，同时恶意伪造 X-Tenant-ID 请求头
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-ID", "malicious_spoofed_tenant");

        MockHttpServletResponse response = new MockHttpServletResponse();

        // 3. 在下游 FilterChain 中捕获并验证已绑定的 TenantContext
        final String[] capturedTenantInChain = new String[1];
        FilterChain verifyChain = (req, res) -> {
            capturedTenantInChain[0] = TenantContext.get();
        };

        // 4. 执行过滤器
        jwtFilter.doFilter(request, response, verifyChain);

        // 5. 断言：租户上下文已从 JWT Claim 中重新权威绑定为 "tenant-enterprise-real-001"，而非伪造的 "malicious_spoofed_tenant"
        assertEquals("tenant-enterprise-real-001", capturedTenantInChain[0],
                "TenantContext 必须从数字签名的 JWT Claim 重新权威绑定，覆盖客户端不可信请求头");
    }
}
