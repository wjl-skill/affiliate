package com.affiliate.platform.security;

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
}
