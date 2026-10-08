package com.affiliate.platform.security.system;

import com.affiliate.platform.security.auth.JwtTokenService;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/**
 * 系统安全与权限中心 REST 控制器 (System Security & RBAC Admin Controller)
 * <p>
 * 提供用户管理、角色定义、树形菜单维护及权限字典服务接口。
 * 具备防提权边界校验与跨租户操作隔离。
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

    private Optional<JwtTokenService.TokenPrincipal> getAuthenticatedPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtTokenService.TokenPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    private void enforceTenantAndRoleGovernance(String targetTenantId, Collection<String> targetRoles) {
        getAuthenticatedPrincipal().ifPresent(principal -> {
            boolean isSuperAdmin = principal.roles().contains("SUPER_ADMIN");
            // 1. 只有超级管理员或管理员可调用
            if (!isSuperAdmin && !principal.roles().contains("ADMIN")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可执行系统安全与授权管理操作");
            }
            // 2. 跨租户校验：非超级管理员只能在自身租户下操作
            if (!isSuperAdmin && targetTenantId != null && !targetTenantId.isBlank()) {
                String callerTenantId = principal.tenantId() != null ? principal.tenantId() : TenantContext.get();
                if (callerTenantId != null && !callerTenantId.equalsIgnoreCase(targetTenantId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "租户管理员无权跨租户操作 (目标租户: " + targetTenantId + ")");
                }
            }
            // 3. 提权防范：非超级管理员禁止分配 SUPER_ADMIN
            if (!isSuperAdmin && targetRoles != null) {
                if (targetRoles.contains("SUPER_ADMIN")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "非超级管理员禁止分配 SUPER_ADMIN 权限");
                }
            }
        });
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

        enforceTenantAndRoleGovernance(tenantId, rolesList);

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
        UserAccount target = userService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
        enforceTenantAndRoleGovernance(target.tenantId(), target.roles());
        return userService.toggleStatus(id, status);
    }

    @PostMapping("/users/{id}/roles")
    public UserAccount assignUserRoles(@PathVariable String id, @RequestBody Set<String> roles) {
        UserAccount target = userService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
        enforceTenantAndRoleGovernance(target.tenantId(), roles);
        return userService.assignRoles(id, roles);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        UserAccount target = userService.find(id).orElse(null);
        if (target != null) {
            enforceTenantAndRoleGovernance(target.tenantId(), target.roles());
        }
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
        String tenantId = (String) body.getOrDefault("tenantId", "tenant-1");

        enforceTenantAndRoleGovernance(tenantId, List.of(roleCode));

        RoleDefinition role = new RoleDefinition(
                id,
                tenantId,
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
        enforceTenantAndRoleGovernance(null, null);
        return roleService.assignPermissions(id, permissions);
    }

    @PostMapping("/roles/{id}/menus")
    public RoleDefinition assignRoleMenus(@PathVariable String id, @RequestBody Set<String> menuIds) {
        enforceTenantAndRoleGovernance(null, null);
        return roleService.assignMenus(id, menuIds);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable String id) {
        enforceTenantAndRoleGovernance(null, null);
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
        if (menu.id() == null || menu.id().isBlank()) {
            menu = menu.withId("menu-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
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
