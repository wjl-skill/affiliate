package com.affiliate.platform.security.system;

import java.time.Instant;
import java.util.Set;

/**
 * 角色定义实体 (RBAC Role Definition Entity)
 * <p>
 * 维护系统角色、多维数据范围 (DataScope) 及挂接的功能权限码集合与菜单集合。
 */
public record RoleDefinition(
        String id,
        String tenantId,
        String roleCode,
        String roleName,
        String description,
        DataScope dataScope,
        Set<String> permissionCodes,
        Set<String> menuIds,
        boolean isSystem,
        Status status,
        Instant createdAt
) {
    public enum DataScope {
        /** 全局所有数据 (平台超管) */
        ALL,
        /** 当前租户空间数据 */
        TENANT_ONLY,
        /** 部门组织数据 */
        DEPT_ONLY,
        /** 仅本人创建的数据 */
        SELF_ONLY
    }

    public enum Status {
        ACTIVE,
        DISABLED
    }

    public RoleDefinition withPermissions(Set<String> newPerms) {
        return new RoleDefinition(id, tenantId, roleCode, roleName, description, dataScope, newPerms, menuIds, isSystem, status, createdAt);
    }

    public RoleDefinition withMenus(Set<String> newMenus) {
        return new RoleDefinition(id, tenantId, roleCode, roleName, description, dataScope, permissionCodes, newMenus, isSystem, status, createdAt);
    }

    public RoleDefinition withStatus(Status newStatus) {
        return new RoleDefinition(id, tenantId, roleCode, roleName, description, dataScope, permissionCodes, menuIds, isSystem, newStatus, createdAt);
    }
}
