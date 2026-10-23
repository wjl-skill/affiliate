package com.affiliate.platform.security.system;

import com.affiliate.platform.entity.SysMenuEntity;
import com.affiliate.platform.mapper.SysMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 树形动态菜单管理服务 (Dynamic Menu Management Service - MyBatis-Plus)
 * <p>
 * 全面基于 MyBatis-Plus 接入 PostgreSQL 真实持久化存储，操作表 `sys_menu`，并具备离线内存回退保护能力。
 */
@Service
public class MenuManagementService {

    private final SysMenuMapper menuMapper;
    private final ConcurrentMap<String, SysMenu> fallbackStore = new ConcurrentHashMap<>();

    public MenuManagementService() {
        this(null);
    }

    @Autowired
    public MenuManagementService(@Autowired(required = false) SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
        ensureDefaultMenus();
    }

    private void ensureDefaultMenus() {
        List<SysMenu> defaults = buildDefaultMenuDefinitions();
        for (SysMenu def : defaults) {
            fallbackStore.put(def.id(), def);
        }
        if (menuMapper != null) {
            try {
                for (SysMenu def : defaults) {
                    if (menuMapper.selectById(def.id()) == null) {
                        save(def);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private List<SysMenu> buildDefaultMenuDefinitions() {
        return List.of(
                // 业务中心菜单 (1-14)
                new SysMenu("menu-dashboard", "0", "监控大盘", "📊", "/", "pages/index.vue", null, 1, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-offers", "0", "Offer 推广计划", "🎯", "/offers", "pages/offers/index.vue", "offer:read", 2, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-smartlinks", "0", "SmartLink 分流", "⚡", "/smartlinks", "pages/smartlinks/index.vue", "smartlink:manage", 3, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-affiliates", "0", "渠道客管理", "🤝", "/affiliates", "pages/affiliates/index.vue", "affiliate:read", 4, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-conversions", "0", "转化与 S2S 归因", "🔄", "/conversions", "pages/conversions/index.vue", "conversion:audit", 5, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-antifraud", "0", "4D反作弊风控", "🛡️", "/affiliate/anti-fraud", "pages/affiliate/anti-fraud.vue", "affiliate:antifraud:read", 6, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-finance", "0", "财务出账结算", "💰", "/finance", "pages/finance/index.vue", "finance:settle", 7, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-payouts", "0", "批量打款与结汇", "💳", "/billing/payouts", "pages/billing/payouts.vue", "finance:settle", 8, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-analytics", "0", "Sub-ID 多维报表", "📈", "/analytics", "pages/analytics/index.vue", "report:analytics", 9, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-cohort", "0", "Cohort留存与LTV", "🧬", "/analytics/cohort", "pages/analytics/cohort.vue", "report:analytics", 10, true, SysMenu.Status.ACTIVE),
                buildMacrosMenu(),
                new SysMenu("menu-adx-deals", "0", "ADX 私有交易 (PMP)", "🏛️", "/adx/deals", "pages/adx/deals.vue", "adx:deal:manage", 12, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-dsp-campaigns", "0", "DSP 预算与控频", "🎯", "/dsp/campaigns", "pages/dsp/campaigns.vue", "dsp:campaign:manage", 13, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-creative-studio", "0", "AI 创意与智能混剪", "🎬", "/creative/studio", "pages/creative/studio.vue", "creative:studio:manage", 14, true, SysMenu.Status.ACTIVE),
                // 系统管理中心菜单 (20-26)
                new SysMenu("menu-sys-users", "0", "用户管理", "👥", "/system/users", "pages/system/users.vue", "system:user:read", 20, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-sys-roles", "0", "角色管理", "🛡️", "/system/roles", "pages/system/roles.vue", "system:role:read", 21, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-sys-menus", "0", "菜单管理", "📑", "/system/menus", "pages/system/menus.vue", "system:menu:manage", 22, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-sys-perms", "0", "权限字典", "🔑", "/system/permissions", "pages/system/permissions.vue", "system:role:read", 23, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-sys-s3", "0", "S3 存储配置", "🗄️", "/system/s3", "pages/system/s3.vue", "system:s3:read", 24, true, SysMenu.Status.ACTIVE),
                new SysMenu("menu-sys-domains", "0", "域名池管理", "🌐", "/system/domains", "pages/system/domains.vue", "system:domain:read", 25, true, SysMenu.Status.ACTIVE),
                buildAiMenu()
        );
    }

    private SysMenu buildAiMenu() {
        return new SysMenu("menu-sys-ai", "0", "AI 模型配置", "🤖", "/system/ai",
                "pages/system/ai.vue", "system:ai:read", 26, true, SysMenu.Status.ACTIVE);
    }

    private SysMenu buildMacrosMenu() {
        return new SysMenu("menu-macros", "0", "宏参数与映射", "🧩", "/affiliate/macros",
                "pages/affiliate/macros.vue", "affiliate:macro:manage", 11, true, SysMenu.Status.ACTIVE);
    }

    public SysMenu save(SysMenu menu) {
        fallbackStore.put(menu.id(), menu);
        if (menuMapper != null) {
            try {
                SysMenuEntity entity = new SysMenuEntity(
                        menu.id(),
                        menu.parentId(),
                        menu.title(),
                        menu.icon(),
                        menu.path(),
                        menu.component(),
                        menu.permissionCode(),
                        menu.sortOrder(),
                        menu.visible(),
                        menu.status().name(),
                        Instant.now()
                );

                if (menuMapper.selectById(menu.id()) != null) {
                    menuMapper.updateById(entity);
                } else {
                    menuMapper.insert(entity);
                }
            } catch (Exception ignored) {
            }
        }
        return menu;
    }

    public Optional<SysMenu> find(String id) {
        if (menuMapper != null) {
            try {
                SysMenuEntity entity = menuMapper.selectById(id);
                return Optional.ofNullable(entity).map(this::toDomain);
            } catch (Exception ignored) {
            }
        }
        return Optional.ofNullable(fallbackStore.get(id));
    }

    public boolean delete(String id) {
        if (menuMapper != null) {
            try {
                return menuMapper.deleteById(id) > 0;
            } catch (Exception ignored) {
            }
        }
        return fallbackStore.remove(id) != null;
    }

    public List<SysMenu> listAll() {
        if (menuMapper != null) {
            try {
                QueryWrapper<SysMenuEntity> qw = new QueryWrapper<>();
                qw.orderByAsc("sort_order");
                List<SysMenuEntity> list = menuMapper.selectList(qw);
                return list.stream().map(this::toDomain).toList();
            } catch (Exception ignored) {
            }
        }
        List<SysMenu> list = new ArrayList<>(fallbackStore.values());
        list.sort(Comparator.comparingInt(SysMenu::sortOrder));
        return list;
    }

    public List<SysMenu> getMenuTree() {
        return buildTree(listAll(), "0");
    }

    public List<SysMenu> getUserMenuTree(Set<String> roles) {
        if (roles != null && roles.contains("SUPER_ADMIN")) {
            return getMenuTree();
        }
        List<SysMenu> available = listAll().stream()
                .filter(m -> !m.path().startsWith("/system/roles") && !m.path().startsWith("/system/menus"))
                .toList();
        return buildTree(available, "0");
    }

    private List<SysMenu> buildTree(List<SysMenu> flatList, String parentId) {
        List<SysMenu> tree = new ArrayList<>();
        for (SysMenu item : flatList) {
            if (Objects.equals(item.parentId(), parentId)) {
                List<SysMenu> children = buildTree(flatList, item.id());
                tree.add(item.withChildren(children));
            }
        }
        tree.sort(Comparator.comparingInt(SysMenu::sortOrder));
        return tree;
    }

    private SysMenu toDomain(SysMenuEntity e) {
        return new SysMenu(
                e.getId(),
                e.getParentId(),
                e.getTitle(),
                e.getIcon(),
                e.getPath(),
                e.getComponent(),
                e.getPermissionCode(),
                e.getSortOrder() != null ? e.getSortOrder() : 0,
                Boolean.TRUE.equals(e.getVisible()),
                SysMenu.Status.valueOf(e.getStatus())
        );
    }
}
