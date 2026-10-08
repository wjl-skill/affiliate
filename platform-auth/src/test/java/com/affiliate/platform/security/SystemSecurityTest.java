package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.JwtTokenService;
import com.affiliate.platform.security.system.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SystemSecurityTest {

    private UserAccountService userService;
    private RoleManagementService roleService;
    private MenuManagementService menuService;
    private PermissionRegistryService permissionService;
    private SystemSecurityController controller;

    @BeforeEach
    void setUp() {
        userService = new UserAccountService();
        roleService = new RoleManagementService();
        menuService = new MenuManagementService();
        permissionService = new PermissionRegistryService();
        controller = new SystemSecurityController(userService, roleService, menuService, permissionService);
    }

    @Test
    void userLifecycleAndRoleAssignment() {
        // 创建新用户
        UserAccount created = controller.createUser(Map.of(
                "username", "test_trafficker",
                "displayName", "Test Trafficker",
                "roles", List.of("TRAFFICKER")
        ));
        assertNotNull(created);
        assertEquals("test_trafficker", created.username());
        assertTrue(created.roles().contains("TRAFFICKER"));

        // 修改状态
        UserAccount disabled = controller.toggleUserStatus(created.id(), UserAccount.Status.DISABLED);
        assertEquals(UserAccount.Status.DISABLED, disabled.status());

        // 重新分配角色
        UserAccount reassigned = controller.assignUserRoles(created.id(), Set.of("FINANCE_OFFICER"));
        assertTrue(reassigned.roles().contains("FINANCE_OFFICER"));
        assertFalse(reassigned.roles().contains("TRAFFICKER"));
    }

    @Test
    void roleAndMenuTreeFilter() {
        List<RoleDefinition> roles = controller.listRoles();
        assertFalse(roles.isEmpty());
        assertTrue(roles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.roleCode())));

        // 测试全量菜单树
        List<SysMenu> fullTree = controller.listMenus();
        assertFalse(fullTree.isEmpty());

        // 测试角色菜单树过滤
        List<SysMenu> userTree = controller.getUserMenuTree("AFFILIATE_MANAGER");
        assertFalse(userTree.isEmpty());
        // AFFILIATE_MANAGER 无法直接访问系统角色管理与菜单管理菜单
        assertTrue(userTree.stream().noneMatch(m -> m.path().equals("/system/roles")));
    }

    @Test
    void permissionsCatalogGrouped() {
        Map<String, List<PermissionDefinition>> grouped = controller.listPermissions();
        assertNotNull(grouped);
        assertTrue(grouped.containsKey("用户管理"));
        assertTrue(grouped.containsKey("Offer计划"));
        assertTrue(grouped.containsKey("S3配置"));
    }

    @Test
    void preventPrivilegeEscalationAndCrossTenantViolation() {
        // 模拟普通租户管理员 (tenant-A, ROLE_ADMIN)
        JwtTokenService.TokenPrincipal adminPrincipal = new JwtTokenService.TokenPrincipal(
                "usr-admin", "admin_a", "Admin A", "tenant-A", List.of("ADMIN"), java.time.Instant.now().plusSeconds(3600)
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        adminPrincipal, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        // 1. 尝试越权分配 SUPER_ADMIN，应当抛出 403
        org.springframework.web.server.ResponseStatusException ex1 = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.createUser(Map.of(
                        "username", "hacker_user",
                        "tenantId", "tenant-A",
                        "roles", List.of("SUPER_ADMIN")
                ))
        );
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex1.getStatusCode());

        // 2. 尝试跨租户创建账号至 tenant-B，应当抛出 403
        org.springframework.web.server.ResponseStatusException ex2 = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.createUser(Map.of(
                        "username", "cross_tenant_user",
                        "tenantId", "tenant-B",
                        "roles", List.of("OPERATOR")
                ))
        );
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex2.getStatusCode());

        // 3. 在自身租户创建普通角色成功
        UserAccount validAccount = controller.createUser(Map.of(
                "username", "valid_operator",
                "tenantId", "tenant-A",
                "roles", List.of("OPERATOR")
        ));
        assertNotNull(validAccount);
        assertEquals("tenant-A", validAccount.tenantId());

        // 清理安全上下文
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
