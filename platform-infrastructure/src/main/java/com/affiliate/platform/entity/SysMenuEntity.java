package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 树形动态菜单实体 (System Menu MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `sys_menu`。
 */
@TableName("sys_menu")
public class SysMenuEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String parentId;
    private String title;
    private String icon;
    private String path;
    private String component;
    private String permissionCode;
    private Integer sortOrder;
    private Boolean visible;
    private String status;
    private Instant createdAt;

    public SysMenuEntity() {}

    public SysMenuEntity(String id, String parentId, String title, String icon, String path,
                         String component, String permissionCode, Integer sortOrder,
                         Boolean visible, String status, Instant createdAt) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.icon = icon;
        this.path = path;
        this.component = component;
        this.permissionCode = permissionCode;
        this.sortOrder = sortOrder;
        this.visible = visible;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
