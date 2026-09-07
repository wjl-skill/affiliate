package com.affiliate.platform.security.system;

import java.time.Instant;
import java.util.Set;

/**
 * 系统用户账号实体 (System User Account Entity)
 * <p>
 * 封装多租户体系下的管理员、商务经理、财务出纳及观察员账户属性。
 */
public record UserAccount(
        String id,
        String tenantId,
        String username,
        String displayName,
        String email,
        String phone,
        String avatar,
        Status status,
        Set<String> roles,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        /** 正常启用 */
        ACTIVE,
        /** 已冻结禁用 */
        DISABLED,
        /** 安全锁定 */
        LOCKED
    }

    public UserAccount withStatus(Status newStatus) {
        return new UserAccount(id, tenantId, username, displayName, email, phone, avatar, newStatus, roles, lastLoginAt, createdAt, Instant.now());
    }

    public UserAccount withRoles(Set<String> newRoles) {
        return new UserAccount(id, tenantId, username, displayName, email, phone, avatar, status, newRoles, lastLoginAt, createdAt, Instant.now());
    }

    public UserAccount withLastLogin(Instant loginTime) {
        return new UserAccount(id, tenantId, username, displayName, email, phone, avatar, status, roles, loginTime, createdAt, Instant.now());
    }
}
