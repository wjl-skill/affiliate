package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceTest {

    private PermissionService perm;
    private RbacService rbacService;

    @BeforeEach
    void setUp() {
        rbacService = new RbacService();
        perm = new PermissionService(rbacService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("未登录匿名用户必须拒绝所有原子权限")
    void testAnonymousHasNoPermissions() {
        assertThat(perm.has("billing:read")).isFalse();
        assertThat(perm.has("campaign:write")).isFalse();
        assertThat(perm.has("")).isFalse();
        assertThat(perm.has(null)).isFalse();
    }

    @Test
    @DisplayName("平台超级管理员 (SUPER_ADMIN) 具备全量通配放行权限")
    void testSuperAdminHasAllPermissions() {
        JwtTokenService.TokenPrincipal principal = new JwtTokenService.TokenPrincipal(
                "u-super", "superadmin", "Super Admin", "tenant-001",
                List.of("SUPER_ADMIN"), Instant.now().plusSeconds(3600)
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(perm.has("billing:write")).isTrue();
        assertThat(perm.has("payout:disburse")).isTrue();
        assertThat(perm.has("campaign:write")).isTrue();
        assertThat(perm.has("unknown:resource:action")).isTrue();
        assertThat(perm.hasRole("SUPER_ADMIN")).isTrue();
    }

    @Test
    @DisplayName("财务人员 (FINANCE) 仅具备资金记账与放款权限，严禁投放写操作")
    void testFinanceRolePermissions() {
        JwtTokenService.TokenPrincipal principal = new JwtTokenService.TokenPrincipal(
                "u-fin", "cfo_user", "Finance Director", "tenant-001",
                List.of("FINANCE"), Instant.now().plusSeconds(3600)
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_FINANCE"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(perm.has("billing:read")).isTrue();
        assertThat(perm.has("billing:write")).isTrue();
        assertThat(perm.has("wallet:recharge")).isTrue();
        assertThat(perm.has("payout:disburse")).isTrue();

        // 越权阻断断言
        assertThat(perm.has("campaign:write")).isFalse();
        assertThat(perm.has("offer:write")).isFalse();
        assertThat(perm.has("creative:approve")).isFalse();
    }

    @Test
    @DisplayName("广告投放人员 (TRAFFICKER) 仅具备物料与计划权限，严禁触碰财务放款")
    void testTraffickerRolePermissions() {
        JwtTokenService.TokenPrincipal principal = new JwtTokenService.TokenPrincipal(
                "u-traf", "ad_ops", "Trafficker One", "tenant-001",
                List.of("TRAFFICKER"), Instant.now().plusSeconds(3600)
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TRAFFICKER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(perm.has("campaign:write")).isTrue();
        assertThat(perm.has("offer:write")).isTrue();
        assertThat(perm.has("smartlink:manage")).isTrue();
        assertThat(perm.has("deal:write")).isTrue();

        // 严禁资金越权
        assertThat(perm.has("wallet:recharge")).isFalse();
        assertThat(perm.has("payout:disburse")).isFalse();
        assertThat(perm.has("finance:settle")).isFalse();
    }
}
