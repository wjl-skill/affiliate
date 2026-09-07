package com.affiliate.platform.security.system;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * 系统安全与权限中心 REST 控制器 (System Security & RBAC Admin Controller)
 * <p>
 * 提供用户管理、角色定义、树形菜单维护及权限字典服务接口。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemSecurityController {

    private final UserAccountService userService;
    private final RoleManagementService roleService;
    private final MenuManagementService menuService;
    private final PermissionRegistryService permissionService;

    public SystemSecurityController(
            UserAccountService userService,
            RoleManagementService roleService,
            MenuManagementService menuService,
            PermissionRegistryService permissionService
    ) {
        this.userService = userService;
        this.roleService = roleService;
        this.menuService = menuService;
        this.permissionService = permissionService;
    }

    // ==========================================
    // 1. 用户管理 (User Accounts)
    // ==========================================
    @GetMapping("/users")
    public List<UserAccount> listUsers() {
        return userService.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccount createUser(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", "usr-" + System.currentTimeMillis() % 100000);
        String username = (String) body.get("username");
        String displayName = (String) body.getOrDefault("displayName", username);
        String email = (String) body.getOrDefault("email", username + "@affiliate.io");
        String phone = (String) body.getOrDefault("phone", "");
        String tenantId = (String) body.getOrDefault("tenantId", "tenant-1");
        List<String> rolesList = (List<String>) body.getOrDefault("roles", List.of("VIEWER"));

        UserAccount account = new UserAccount(
                id,
                tenantId,
                username,
                displayName,
                email,
                phone,
                "https://api.dicebear.com/7.x/bottts/svg?seed=" + username,
                UserAccount.Status.ACTIVE,
                new HashSet<>(rolesList),
                null,
                Instant.now(),
                Instant.now()
        );
        return userService.saveUser(account);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserAccount> getUser(@PathVariable String id) {
        return userService.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/status")
    public UserAccount toggleUserStatus(@PathVariable String id, @RequestParam UserAccount.Status status) {
        return userService.toggleStatus(id, status);
    }

    @PostMapping("/users/{id}/roles")
    public UserAccount assignUserRoles(@PathVariable String id, @RequestBody Set<String> roles) {
        return userService.assignRoles(id, roles);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        boolean deleted = userService.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // 2. 角色管理 (Role Management)
    // ==========================================
    @GetMapping("/roles")
    public List<RoleDefinition> listRoles() {
        return roleService.listAll();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDefinition createRole(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", "role-" + System.currentTimeMillis() % 10000);
        String roleCode = (String) body.get("roleCode");
        String roleName = (String) body.getOrDefault("roleName", roleCode);
        String description = (String) body.getOrDefault("description", "");
        String dataScopeStr = (String) body.getOrDefault("dataScope", "TENANT_ONLY");

        RoleDefinition role = new RoleDefinition(
                id,
                "tenant-1",
                roleCode,
                roleName,
                description,
                RoleDefinition.DataScope.valueOf(dataScopeStr),
                Set.of(),
                Set.of(),
                false,
                RoleDefinition.Status.ACTIVE,
                Instant.now()
        );
        return roleService.saveRole(role);
    }

    @PostMapping("/roles/{id}/permissions")
    public RoleDefinition assignRolePermissions(@PathVariable String id, @RequestBody Set<String> permissions) {
        return roleService.assignPermissions(id, permissions);
    }

    @PostMapping("/roles/{id}/menus")
    public RoleDefinition assignRoleMenus(@PathVariable String id, @RequestBody Set<String> menuIds) {
        return roleService.assignMenus(id, menuIds);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable String id) {
        boolean deleted = roleService.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // 3. 权限字典 (Permissions Catalog)
    // ==========================================
    @GetMapping("/permissions")
    public Map<String, List<PermissionDefinition>> listPermissions() {
        return permissionService.listGroupedByModule();
    }

    // ==========================================
    // 4. 树形菜单管理 (Menus & Dynamic Tree)
    // ==========================================
    @GetMapping("/menus")
    public List<SysMenu> listMenus() {
        return menuService.getMenuTree();
    }

    @PostMapping("/menus")
    @ResponseStatus(HttpStatus.CREATED)
    public SysMenu saveMenu(@Valid @RequestBody SysMenu menu) {
        return menuService.save(menu);
    }

    @DeleteMapping("/menus/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable String id) {
        boolean deleted = menuService.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/menus/user-tree")
    public List<SysMenu> getUserMenuTree(@RequestParam(required = false, defaultValue = "SUPER_ADMIN") String role) {
        return menuService.getUserMenuTree(Set.of(role));
    }
}
