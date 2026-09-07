package com.affiliate.platform.security.system;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态树形菜单实体 (Dynamic System Menu Entity)
 * <p>
 * 支撑前端侧边栏根据角色权限自适应动态挂载路由与导航层级。
 */
public record SysMenu(
        String id,
        String parentId,
        String title,
        String icon,
        String path,
        String component,
        String permissionCode,
        int sortOrder,
        boolean visible,
        Status status,
        List<SysMenu> children
) {
    public enum Status {
        ACTIVE,
        DISABLED
    }

    public SysMenu(
            String id,
            String parentId,
            String title,
            String icon,
            String path,
            String component,
            String permissionCode,
            int sortOrder,
            boolean visible,
            Status status
    ) {
        this(id, parentId, title, icon, path, component, permissionCode, sortOrder, visible, status, new ArrayList<>());
    }

    public SysMenu withChildren(List<SysMenu> newChildren) {
        return new SysMenu(id, parentId, title, icon, path, component, permissionCode, sortOrder, visible, status, newChildren);
    }
}
