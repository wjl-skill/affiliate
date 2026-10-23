package com.affiliate.platform.security.system;

import com.affiliate.platform.security.auth.JwtTokenService;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 提供用户管理、角色定义、树形菜单维护、权限字典服务及安全审计事件 (`audit_event`) 查询接口。
 * 具备防提权边界校验与跨租户操作隔离。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemSecurityController {

    private final UserAccountService userService;
    private final RoleManagementService roleService;
    private final MenuManagementService menuService;
    private final PermissionRegistryService permissionService;
    private final SecurityAuditService securityAuditService;

    @Autowired
    public SystemSecurityController(
            UserAccountService userService,
            RoleManagementService roleService,
            MenuManagementService menuService,
            PermissionRegistryService permissionService,
            @Autowired(required = false) SecurityAuditService securityAuditService
    ) {
        this.userService = userService;
        this.roleService = roleService;
        this.menuService = menuService;
        this.permissionService = permissionService;
        this.securityAuditService = securityAuditService != null ? securityAuditService : new SecurityAuditService();
    }

    public SystemSecurityController(
            UserAccountService userService,
            RoleManagementService roleService,
            MenuManagementService menuService,
            PermissionRegistryService permissionService
    ) {
        this(userService, roleService, menuService, permissionService, new SecurityAuditService());
    }

    private Optional<JwtTokenService.TokenPrincipal> getAuthenticatedPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtTokenService.TokenPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    private String currentActorId() {
        return getAuthenticatedPrincipal().map(JwtTokenService.TokenPrincipal::userId).orElse("admin");
    }

    private String currentTenantId(String fallbackTenant) {
        if (fallbackTenant != null && !fallbackTenant.isBlank()) {
            return fallbackTenant;
        }
        return getAuthenticatedPrincipal()
                .map(JwtTokenService.TokenPrincipal::tenantId)
                .orElseGet(() -> TenantContext.get() != null ? TenantContext.get() : "tenant-1");
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
        UserAccount saved = userService.saveUser(account);
        securityAuditService.recordEvent(
                tenantId,
                currentActorId(),
                "CREATE_USER",
                "USER_ACCOUNT",
                saved.id(),
                "{\"username\":\"" + username + "\",\"roles\":" + rolesList + "}"
        );
        return saved;
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
        UserAccount updated = userService.toggleStatus(id, status);
        securityAuditService.recordEvent(
                target.tenantId(),
                currentActorId(),
                "UPDATE_USER_STATUS",
                "USER_ACCOUNT",
                id,
                "{\"status\":\"" + status + "\"}"
        );
        return updated;
    }

    @PostMapping("/users/{id}/roles")
    public UserAccount assignUserRoles(@PathVariable String id, @RequestBody Set<String> roles) {
        UserAccount target = userService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
        enforceTenantAndRoleGovernance(target.tenantId(), roles);
        UserAccount updated = userService.assignRoles(id, roles);
        securityAuditService.recordEvent(
                target.tenantId(),
                currentActorId(),
                "ASSIGN_USER_ROLES",
                "USER_ACCOUNT",
                id,
                "{\"roles\":" + roles + "}"
        );
        return updated;
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        UserAccount target = userService.find(id).orElse(null);
        if (target != null) {
            enforceTenantAndRoleGovernance(target.tenantId(), target.roles());
        }
        boolean deleted = userService.delete(id);
        if (deleted) {
            securityAuditService.recordEvent(
                    currentTenantId(target != null ? target.tenantId() : null),
                    currentActorId(),
                    "DELETE_USER",
                    "USER_ACCOUNT",
                    id,
                    "{}"
            );
        }
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
        RoleDefinition saved = roleService.saveRole(role);
        securityAuditService.recordEvent(
                tenantId,
                currentActorId(),
                "CREATE_ROLE",
                "ROLE_DEFINITION",
                saved.id(),
                "{\"roleCode\":\"" + roleCode + "\",\"dataScope\":\"" + dataScopeStr + "\"}"
        );
        return saved;
    }

    @PostMapping("/roles/{id}/permissions")
    public RoleDefinition assignRolePermissions(@PathVariable String id, @RequestBody Set<String> permissions) {
        enforceTenantAndRoleGovernance(null, null);
        RoleDefinition saved = roleService.assignPermissions(id, permissions);
        securityAuditService.recordEvent(
                currentTenantId(saved.tenantId()),
                currentActorId(),
                "ASSIGN_ROLE_PERMISSIONS",
                "ROLE_DEFINITION",
                id,
                "{\"permissionCount\":" + (permissions != null ? permissions.size() : 0) + "}"
        );
        return saved;
    }

    @PostMapping("/roles/{id}/menus")
    public RoleDefinition assignRoleMenus(@PathVariable String id, @RequestBody Set<String> menuIds) {
        enforceTenantAndRoleGovernance(null, null);
        RoleDefinition saved = roleService.assignMenus(id, menuIds);
        securityAuditService.recordEvent(
                currentTenantId(saved.tenantId()),
                currentActorId(),
                "ASSIGN_ROLE_MENUS",
                "ROLE_DEFINITION",
                id,
                "{\"menuCount\":" + (menuIds != null ? menuIds.size() : 0) + "}"
        );
        return saved;
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable String id) {
        enforceTenantAndRoleGovernance(null, null);
        boolean deleted = roleService.delete(id);
        if (deleted) {
            securityAuditService.recordEvent(
                    currentTenantId(null),
                    currentActorId(),
                    "DELETE_ROLE",
                    "ROLE_DEFINITION",
                    id,
                    "{}"
            );
        }
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
        SysMenu saved = menuService.save(menu);
        securityAuditService.recordEvent(
                currentTenantId(null),
                currentActorId(),
                "SAVE_MENU",
                "SYS_MENU",
                saved.id(),
                "{\"title\":\"" + saved.title() + "\",\"path\":\"" + saved.path() + "\"}"
        );
        return saved;
    }

    @DeleteMapping("/menus/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable String id) {
        boolean deleted = menuService.delete(id);
        if (deleted) {
            securityAuditService.recordEvent(
                    currentTenantId(null),
                    currentActorId(),
                    "DELETE_MENU",
                    "SYS_MENU",
                    id,
                    "{}"
            );
        }
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/menus/user-tree")
    public List<SysMenu> getUserMenuTree(@RequestParam(required = false, defaultValue = "SUPER_ADMIN") String role) {
        return menuService.getUserMenuTree(Set.of(role));
    }

    // ==========================================
    // 5. 安全审计事件流水 (Audit Events)
    // ==========================================
    @GetMapping("/audit-events")
    public List<SecurityAuditService.AuditEventRecord> listAuditEvents(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        return securityAuditService.listEvents(currentTenantId(tenantId), limit);
    }
}
