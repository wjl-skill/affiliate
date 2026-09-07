package com.affiliate.platform.security;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 细粒度角色与基于属性的访问控制服务 (RBAC Authorization Service)
 * <p>
 * 定义企业级标准角色与功能权限对应拓扑：
 * 1. SUPER_ADMIN：超级管理员，具备全量资源通配权限；
 * 2. TENANT_ADMIN：租户管理员，负责租户配置与人员权限分配；
 * 3. TRAFFICKER：广告投放运营人员，负责活动、广告组定向策略与物料提交；
 * 4. AUDITOR：安全合规审核员，负责物料审核与敏感资质判定；
 * 5. FINANCE：财务运营人员，负责资金充值、扣款账单与媒体分成结算；
 * 6. VIEWER：观察者只读角色，仅可查看报表分析。
 */
@Service
public class RbacService {

    public enum Role {
        SUPER_ADMIN,
        TENANT_ADMIN,
        TRAFFICKER,
        AUDITOR,
        FINANCE,
        VIEWER
    }

    private static final Map<Role, Set<String>> ROLE_PERMISSIONS = new EnumMap<>(Role.class);

    static {
        ROLE_PERMISSIONS.put(Role.SUPER_ADMIN, Set.of("*"));
        ROLE_PERMISSIONS.put(Role.TENANT_ADMIN, Set.of(
                "campaign:read", "campaign:write",
                "creative:read", "creative:write", "creative:approve",
                "billing:read", "billing:write",
                "report:read", "tenant:config"
        ));
        ROLE_PERMISSIONS.put(Role.TRAFFICKER, Set.of(
                "campaign:read", "campaign:write",
                "creative:read", "creative:write",
                "report:read"
        ));
        ROLE_PERMISSIONS.put(Role.AUDITOR, Set.of(
                "creative:read", "creative:approve",
                "report:read"
        ));
        ROLE_PERMISSIONS.put(Role.FINANCE, Set.of(
                "billing:read", "billing:write",
                "report:read"
        ));
        ROLE_PERMISSIONS.put(Role.VIEWER, Set.of(
                "campaign:read", "creative:read", "report:read"
        ));
    }

    /**
     * 判断指定角色是否具备目标资源的操作权限
     *
     * @param role               用户所属主体角色
     * @param requiredPermission 目标权限标识 (如 "creative:approve")
     * @return true 代表鉴权通过
     */
    public boolean hasPermission(Role role, String requiredPermission) {
        if (role == null || requiredPermission == null) {
            return false;
        }
        Set<String> permissions = ROLE_PERMISSIONS.get(role);
        if (permissions == null) {
            return false;
        }
        return permissions.contains("*") || permissions.contains(requiredPermission);
    }
}
