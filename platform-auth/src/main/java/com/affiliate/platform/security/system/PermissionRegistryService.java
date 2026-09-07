package com.affiliate.platform.security.system;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 权限字典与资源注册中心服务 (Permission Registry Service)
 */
@Service
public class PermissionRegistryService {

    private final ConcurrentMap<String, PermissionDefinition> registry = new ConcurrentHashMap<>();

    public PermissionRegistryService() {
        initStandardCatalog();
    }

    private void initStandardCatalog() {
        // 用户与权限管理
        register(new PermissionDefinition("system:user:read", "用户查看", "用户管理", "查看系统用户列表与详情", PermissionDefinition.Type.API));
        register(new PermissionDefinition("system:user:write", "用户维护", "用户管理", "创建、编辑或重置用户密码", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("system:role:read", "角色查看", "角色管理", "查看角色清单", PermissionDefinition.Type.API));
        register(new PermissionDefinition("system:role:write", "角色授权", "角色管理", "维护角色与分配权限菜单", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("system:menu:manage", "菜单管理", "菜单配置", "编辑树形菜单与动态路由", PermissionDefinition.Type.BUTTON));

        // 基础设施与域名
        register(new PermissionDefinition("system:s3:read", "存储查看", "S3配置", "查看对象存储桶配置", PermissionDefinition.Type.API));
        register(new PermissionDefinition("system:s3:write", "存储配置", "S3配置", "修改 S3 凭据与连通性测试", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("system:domain:read", "域名查看", "域名管理", "查看跟踪域名池状态", PermissionDefinition.Type.API));
        register(new PermissionDefinition("system:domain:write", "域名配置", "域名管理", "绑定新域名并检测 CNAME", PermissionDefinition.Type.BUTTON));

        // 网盟业务
        register(new PermissionDefinition("offer:read", "计划查询", "Offer计划", "查看推广计划与出价", PermissionDefinition.Type.API));
        register(new PermissionDefinition("offer:write", "计划维护", "Offer计划", "创建与修改推广计划及 Cap", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("smartlink:manage", "分流管理", "SmartLink", "配置 TDS 路由策略与规则", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("affiliate:read", "渠道查看", "渠道客", "查看渠道客档案及短链", PermissionDefinition.Type.API));
        register(new PermissionDefinition("affiliate:write", "渠道维护", "渠道客", "评定等级与配置 Postback", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("conversion:audit", "转化审核", "转化归因", "人工通过或驳回异常订单", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("finance:settle", "账期结算", "财务出账", "生成批次发票与核销打款", PermissionDefinition.Type.BUTTON));
        register(new PermissionDefinition("report:analytics", "多维分析", "报表中心", "查看 Sub-ID 与 ROI 报表", PermissionDefinition.Type.API));
    }

    public void register(PermissionDefinition def) {
        registry.put(def.code(), def);
    }

    public List<PermissionDefinition> listAll() {
        return new ArrayList<>(registry.values());
    }

    public Map<String, List<PermissionDefinition>> listGroupedByModule() {
        Map<String, List<PermissionDefinition>> grouped = new LinkedHashMap<>();
        for (PermissionDefinition def : registry.values()) {
            grouped.computeIfAbsent(def.module(), k -> new ArrayList<>()).add(def);
        }
        return grouped;
    }
}
