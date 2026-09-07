package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 系统用户账号实体 (User Account MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `sys_user_account`。
 */
@TableName("sys_user_account")
public class UserAccountEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String avatar;
    private String passwordHash;
    private String status;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UserAccountEntity() {}

    public UserAccountEntity(String id, String tenantId, String username, String displayName,
                             String email, String phone, String avatar, String passwordHash,
                             String status, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.avatar = avatar;
        this.passwordHash = passwordHash;
        this.status = status;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
