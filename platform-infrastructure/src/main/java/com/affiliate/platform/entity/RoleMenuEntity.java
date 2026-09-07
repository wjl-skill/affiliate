package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 角色-菜单授权关联实体 (Role-Menu Relation Entity)
 * <p>
 * 映射数据库表 `sys_role_menu`。
 */
@TableName("sys_role_menu")
public class RoleMenuEntity {

    private String roleId;
    private String menuId;
    private Instant createdAt;

    public RoleMenuEntity() {}

    public RoleMenuEntity(String roleId, String menuId, Instant createdAt) {
        this.roleId = roleId;
        this.menuId = menuId;
        this.createdAt = createdAt;
    }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getMenuId() { return menuId; }
    public void setMenuId(String menuId) { this.menuId = menuId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
