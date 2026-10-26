package com.affiliate.platform.security;

import org.springframework.stereotype.Service;

import java.util.*;

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
                "budget:read", "budget:write",
                "deal:read", "deal:write", "adslot:read", "adslot:write",
                "billing:read", "billing:write", "billing:admin", "wallet:read", "ledger:reverse",
                "ops:replay",
                "offer:read", "offer:write",
                "smartlink:read", "smartlink:manage",
                "affiliate:read", "affiliate:write",
                "conversion:read", "conversion:audit",
                "report:read", "report:analytics",
                "system:domain:read", "system:s3:read",
                "tenant:read", "tenant:write", "tenant:config"
        ));

        ROLE_PERMISSIONS.put(Role.TRAFFICKER, Set.of(
                "campaign:read", "campaign:write",
                "budget:read", "budget:write",
                "deal:read", "deal:write", "adslot:read", "adslot:write",
                "offer:read", "offer:write",
                "smartlink:read", "smartlink:manage",
                "affiliate:read",
                "creative:read", "creative:write", "creative:submit",
                "report:read", "report:analytics"
        ));

        ROLE_PERMISSIONS.put(Role.AUDITOR, Set.of(
                "creative:read", "creative:approve", "creative:review",
                "conversion:read", "conversion:audit",
                "audit:read",
                "report:read"
        ));

        ROLE_PERMISSIONS.put(Role.FINANCE, Set.of(
                "billing:read", "billing:write", "billing:admin",
                "wallet:read", "wallet:recharge", "wallet:deduct", "wallet:freeze", "ledger:reverse",
                "payout:read", "payout:create", "payout:approve", "payout:disburse", "payout:export",
                "finance:settle",
                "report:read", "report:analytics"
        ));

        ROLE_PERMISSIONS.put(Role.VIEWER, Set.of(
                "campaign:read", "creative:read", "offer:read", "deal:read", "adslot:read",
                "report:read", "report:analytics"
        ));
    }

    /**
     * 判断指定角色是否具备目标资源的操作权限
     *
     * @param role               用户所属主体角色
     * @param requiredPermission 目标权限标识 (如 "billing:write", "payout:approve")
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
        if (permissions.contains("*") || permissions.contains(requiredPermission)) {
            return true;
        }
        // 支持前缀通配符判断，如 "billing:*" 匹配 "billing:read"
        int colonIdx = requiredPermission.indexOf(':');
        if (colonIdx > 0) {
            String domainWildcard = requiredPermission.substring(0, colonIdx) + ":*";
            if (permissions.contains(domainWildcard)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取角色的所有有效权限码
     */
    public Set<String> getPermissionsForRole(Role role) {
        if (role == null) return Collections.emptySet();
        return ROLE_PERMISSIONS.getOrDefault(role, Collections.emptySet());
    }
}
