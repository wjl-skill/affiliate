package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 角色-权限操作码绑定实体 (Role-Permission Relation Entity)
 * <p>
 * 映射数据库表 `sys_role_permission`。
 */
@TableName("sys_role_permission")
public class RolePermissionEntity {

    private String roleId;
    private String permissionCode;
    private Instant createdAt;

    public RolePermissionEntity() {}

    public RolePermissionEntity(String roleId, String permissionCode, Instant createdAt) {
        this.roleId = roleId;
        this.permissionCode = permissionCode;
        this.createdAt = createdAt;
    }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
