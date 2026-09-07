package com.affiliate.platform.security.system;

/**
 * 权限定义字典项 (Permission Definition Item)
 * <p>
 * 细化全系统 13 个业务子域的功能操作按钮与 API 资源粒度权限树。
 */
public record PermissionDefinition(
        String code,
        String name,
        String module,
        String description,
        Type type
) {
    public enum Type {
        BUTTON,
        API,
        MENU
    }
}
