package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 角色定义持久化实体 (Role Definition MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `sys_role_definition`。
 */
@TableName("sys_role_definition")
public class RoleDefinitionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String roleCode;
    private String roleName;
    private String description;
    private String dataScope;
    private Boolean isSystem;
    private String status;
    private Instant createdAt;

    public RoleDefinitionEntity() {}

    public RoleDefinitionEntity(String id, String tenantId, String roleCode, String roleName,
                                String description, String dataScope, Boolean isSystem,
                                String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.dataScope = dataScope;
        this.isSystem = isSystem;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }

    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
