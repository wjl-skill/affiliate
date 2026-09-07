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
 * 全面基于 MyBatis-Plus 接入 PostgreSQL 真实持久化存储，操作表 `sys_menu`。
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
        if (menuMapper != null) {
            try {
                Long count = menuMapper.selectCount(null);
                if (count != null && count == 0) {
                    initDefaultMenus();
                }
            } catch (Exception e) {
                initDefaultMenus();
            }
        } else {
            initDefaultMenus();
        }
    }

    private void initDefaultMenus() {
        save(new SysMenu("menu-dashboard", "0", "监控大盘", "📊", "/", "pages/index.vue", null, 1, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-offers", "0", "Offer 计划", "🎯", "/offers", "pages/offers/index.vue", "offer:read", 2, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-smartlinks", "0", "SmartLink 分流", "⚡", "/smartlinks", "pages/smartlinks/index.vue", "smartlink:manage", 3, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-affiliates", "0", "渠道客管理", "🤝", "/affiliates", "pages/affiliates/index.vue", "affiliate:read", 4, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-conversions", "0", "转化与归因", "🔄", "/conversions", "pages/conversions/index.vue", "conversion:audit", 5, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-finance", "0", "财务账期出账", "💰", "/finance", "pages/finance/index.vue", "finance:settle", 6, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-analytics", "0", "Sub-ID 报表", "📈", "/analytics", "pages/analytics/index.vue", "report:analytics", 7, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-sys-users", "0", "用户管理", "👥", "/system/users", "pages/system/users.vue", "system:user:read", 10, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-sys-roles", "0", "角色管理", "🛡️", "/system/roles", "pages/system/roles.vue", "system:role:read", 11, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-sys-menus", "0", "菜单管理", "📑", "/system/menus", "pages/system/menus.vue", "system:menu:manage", 12, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-sys-perms", "0", "权限字典", "🔑", "/system/permissions", "pages/system/permissions.vue", "system:role:read", 13, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-sys-s3", "0", "S3 存储配置", "🗄️", "/system/s3", "pages/system/s3.vue", "system:s3:read", 14, true, SysMenu.Status.ACTIVE));
        save(new SysMenu("menu-sys-domains", "0", "域名池管理", "🌐", "/system/domains", "pages/system/domains.vue", "system:domain:read", 15, true, SysMenu.Status.ACTIVE));
    }

    public SysMenu save(SysMenu menu) {
        if (menuMapper != null) {
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
            return menu;
        }

        fallbackStore.put(menu.id(), menu);
        return menu;
    }

    public Optional<SysMenu> find(String id) {
        if (menuMapper != null) {
            SysMenuEntity entity = menuMapper.selectById(id);
            return Optional.ofNullable(entity).map(this::toDomain);
        }
        return Optional.ofNullable(fallbackStore.get(id));
    }

    public boolean delete(String id) {
        if (menuMapper != null) {
            return menuMapper.deleteById(id) > 0;
        }
        return fallbackStore.remove(id) != null;
    }

    public List<SysMenu> listAll() {
        if (menuMapper != null) {
            QueryWrapper<SysMenuEntity> qw = new QueryWrapper<>();
            qw.orderByAsc("sort_order");
            List<SysMenuEntity> list = menuMapper.selectList(qw);
            return list.stream().map(this::toDomain).toList();
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
