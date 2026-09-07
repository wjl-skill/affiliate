package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 用户-角色多对多关联实体 (User-Role Relation Entity)
 * <p>
 * 映射数据库表 `sys_user_role`。
 */
@TableName("sys_user_role")
public class UserRoleEntity {

    private String userId;
    private String roleId;
    private Instant createdAt;

    public UserRoleEntity() {}

    public UserRoleEntity(String userId, String roleId, Instant createdAt) {
        this.userId = userId;
        this.roleId = roleId;
        this.createdAt = createdAt;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
