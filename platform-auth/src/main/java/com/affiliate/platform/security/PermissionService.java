package com.affiliate.platform.security;

import com.affiliate.platform.security.auth.JwtTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 细粒度权限评估器 (Spring Security SpEL: @perm.has('resource:action'))
 * <p>
 * 提供统一安全表达式接口，对接 RBAC 矩阵与用户令牌上下文。
 */
@Service("perm")
public class PermissionService {

    private final RbacService rbacService;

    public PermissionService(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * 判断当前调用方是否具备指定的原子权限码
     *
     * @param requiredPermission 权限标识 (如 "billing:write", "wallet:recharge")
     * @return true 代表通过鉴权
     */
    public boolean has(String requiredPermission) {
        if (requiredPermission == null || requiredPermission.isBlank()) {
            return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        // 1. 若拥有平台超级管理员角色或通配权限 "*"，无条件放行
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities != null) {
            for (GrantedAuthority ga : authorities) {
                String a = ga.getAuthority();
                if ("ROLE_SUPER_ADMIN".equalsIgnoreCase(a) || "*".equals(a)) {
                    return true;
                }
                if (a.equalsIgnoreCase(requiredPermission)) {
                    return true;
                }
            }
        }

        // 2. 根据用户角色委托 RbacService 进行权限拓扑判定
        if (auth.getPrincipal() instanceof JwtTokenService.TokenPrincipal principal) {
            if (principal.roles() != null) {
                for (String roleStr : principal.roles()) {
                    try {
                        RbacService.Role role = RbacService.Role.valueOf(roleStr.toUpperCase());
                        if (rbacService.hasPermission(role, requiredPermission)) {
                            return true;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // 忽略未知或非标准角色
                    }
                }
            }
        }

        return false;
    }

    /**
     * 具备其中任意一个权限即视为放行
     */
    public boolean hasAny(String... requiredPermissions) {
        if (requiredPermissions == null) return false;
        for (String perm : requiredPermissions) {
            if (has(perm)) return true;
        }
        return false;
    }

    /**
     * 判定是否具备指定角色
     */
    public boolean hasRole(String roleName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        String target = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equalsIgnoreCase(target));
    }
}
