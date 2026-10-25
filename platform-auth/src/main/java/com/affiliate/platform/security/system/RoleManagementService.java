package com.affiliate.platform.security.system;

import com.affiliate.platform.entity.RoleDefinitionEntity;
import com.affiliate.platform.entity.RoleMenuEntity;
import com.affiliate.platform.entity.RolePermissionEntity;
import com.affiliate.platform.mapper.RoleDefinitionMapper;
import com.affiliate.platform.mapper.RoleMenuMapper;
import com.affiliate.platform.mapper.RolePermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 角色与动态授权管理服务 (Role Management Service - MyBatis-Plus)
 * <p>
 * 全面基于 MyBatis-Plus 接入 PostgreSQL 真实持久化存储，操作表 `sys_role_definition`、`sys_role_permission` 与 `sys_role_menu`，
 * 并具备离线内存回退保护能力。
 */
@Service
public class RoleManagementService {

    private static final Logger log = LoggerFactory.getLogger(RoleManagementService.class);

    private final RoleDefinitionMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final ConcurrentMap<String, RoleDefinition> fallbackStore = new ConcurrentHashMap<>();

    public RoleManagementService() {
        this(null, null, null);
    }

    @Autowired
    public RoleManagementService(
            @Autowired(required = false) RoleDefinitionMapper roleMapper,
            @Autowired(required = false) RolePermissionMapper rolePermissionMapper,
            @Autowired(required = false) RoleMenuMapper roleMenuMapper
    ) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        ensurePresetRoles();
    }

    private void ensurePresetRoles() {
        if (roleMapper != null) {
            try {
                Long count = roleMapper.selectCount(null);
                if (count != null && count == 0) {
                    initDefaultRoles();
                }
            } catch (Exception e) {
                log.warn("Failed to check database preset roles, using memory fallback: {}", e.getMessage());
                initDefaultRolesFallbackOnly();
            }
        } else {
            initDefaultRolesFallbackOnly();
        }
    }

    private void initDefaultRoles() {
        for (RoleDefinition role : buildDefaultRoles()) {
            saveRole(role);
        }
    }

    private void initDefaultRolesFallbackOnly() {
        for (RoleDefinition role : buildDefaultRoles()) {
            fallbackStore.put(role.id(), role);
        }
    }

    private List<RoleDefinition> buildDefaultRoles() {
        return List.of(
                new RoleDefinition(
                        "role-super-admin",
                        "public",
                        "SUPER_ADMIN",
                        "超级管理员",
                        "具备平台全量资源最高通配权限",
                        RoleDefinition.DataScope.ALL,
                        Set.of("*"),
                        Set.of("menu-dashboard", "menu-offers", "menu-smartlinks", "menu-affiliates", "menu-conversions", "menu-antifraud", "menu-finance", "menu-payouts", "menu-analytics", "menu-cohort", "menu-macros", "menu-adx-deals", "menu-dsp-campaigns", "menu-creative-studio", "menu-sys-users", "menu-sys-roles", "menu-sys-menus", "menu-sys-perms", "menu-sys-s3", "menu-sys-domains", "menu-sys-ai"),
                        true,
                        RoleDefinition.Status.ACTIVE,
                        Instant.now()
                ),
                new RoleDefinition(
                        "role-aff-manager",
                        "tenant-1",
                        "AFFILIATE_MANAGER",
                        "网盟商务主管",
                        "负责推广计划、渠道客关系维护及日常转化质检",
                        RoleDefinition.DataScope.TENANT_ONLY,
                        Set.of("offer:read", "offer:write", "smartlink:manage", "affiliate:read", "affiliate:write", "conversion:audit", "report:analytics"),
                        Set.of("menu-dashboard", "menu-offers", "menu-smartlinks", "menu-affiliates", "menu-conversions", "menu-analytics"),
                        false,
                        RoleDefinition.Status.ACTIVE,
                        Instant.now()
                ),
                new RoleDefinition(
                        "role-finance",
                        "tenant-1",
                        "FINANCE_OFFICER",
                        "财务结算专员",
                        "负责账单出账核算、起提门槛审批与打款核销",
                        RoleDefinition.DataScope.TENANT_ONLY,
                        Set.of("finance:settle", "report:analytics"),
                        Set.of("menu-dashboard", "menu-finance", "menu-analytics"),
                        false,
                        RoleDefinition.Status.ACTIVE,
                        Instant.now()
                )
        );
    }

    public RoleDefinition saveRole(RoleDefinition role) {
        fallbackStore.put(role.id(), role);
        if (roleMapper != null) {
            try {
                RoleDefinitionEntity entity = new RoleDefinitionEntity(
                        role.id(),
                        role.tenantId(),
                        role.roleCode(),
                        role.roleName(),
                        role.description(),
                        role.dataScope().name(),
                        role.isSystem(),
                        role.status().name(),
                        role.createdAt() != null ? role.createdAt() : Instant.now()
                );

                if (roleMapper.selectById(role.id()) != null) {
                    roleMapper.updateById(entity);
                } else {
                    roleMapper.insert(entity);
                }

                if (role.permissionCodes() != null) {
                    assignPermissions(role.id(), role.permissionCodes());
                }
                if (role.menuIds() != null) {
                    assignMenus(role.id(), role.menuIds());
                }
            } catch (Exception e) {
                log.warn("Failed to persist role {} to database: {}, using memory store", role.id(), e.getMessage());
            }
        }
        return role;
    }

    public List<RoleDefinition> listAll() {
        if (roleMapper != null) {
            try {
                QueryWrapper<RoleDefinitionEntity> qw = new QueryWrapper<>();
                qw.orderByAsc("created_at");
                List<RoleDefinitionEntity> list = roleMapper.selectList(qw);
                return list.stream().map(this::toDomain).toList();
            } catch (Exception e) {
                log.warn("Failed to list roles from database: {}, using memory store", e.getMessage());
            }
        }
        return new ArrayList<>(fallbackStore.values());
    }

    public Optional<RoleDefinition> find(String id) {
        if (roleMapper != null) {
            try {
                RoleDefinitionEntity entity = roleMapper.selectById(id);
                return Optional.ofNullable(entity).map(this::toDomain);
            } catch (Exception e) {
                log.warn("Failed to find role {} in database: {}, using memory store", id, e.getMessage());
            }
        }
        return Optional.ofNullable(fallbackStore.get(id));
    }

    public boolean delete(String id) {
        if (roleMapper != null) {
            try {
                RoleDefinitionEntity role = roleMapper.selectById(id);
                if (role != null && Boolean.TRUE.equals(role.getIsSystem())) {
                    throw new IllegalArgumentException("内置系统预置角色不可删除！");
                }
                if (rolePermissionMapper != null) {
                    QueryWrapper<RolePermissionEntity> pqw = new QueryWrapper<>();
                    pqw.eq("role_id", id);
                    rolePermissionMapper.delete(pqw);
                }
                if (roleMenuMapper != null) {
                    QueryWrapper<RoleMenuEntity> mqw = new QueryWrapper<>();
                    mqw.eq("role_id", id);
                    roleMenuMapper.delete(mqw);
                }
                return roleMapper.deleteById(id) > 0;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to delete role {} from database: {}, using memory store", id, e.getMessage());
            }
        }

        RoleDefinition role = fallbackStore.get(id);
        if (role != null && role.isSystem()) {
            throw new IllegalArgumentException("内置系统预置角色不可删除！");
        }
        return fallbackStore.remove(id) != null;
    }

    public RoleDefinition assignPermissions(String id, Set<String> perms) {
        if (roleMapper != null && rolePermissionMapper != null) {
            try {
                RoleDefinitionEntity role = roleMapper.selectById(id);
                if (role == null) throw new NoSuchElementException("角色不存在: " + id);

                QueryWrapper<RolePermissionEntity> pqw = new QueryWrapper<>();
                pqw.eq("role_id", id);
                rolePermissionMapper.delete(pqw);

                if (perms != null) {
                    for (String perm : perms) {
                        rolePermissionMapper.insert(new RolePermissionEntity(id, perm, Instant.now()));
                    }
                }
                return toDomain(role);
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to assign permissions to role {} in database: {}, using memory store", id, e.getMessage());
            }
        }

        RoleDefinition role = fallbackStore.get(id);
        if (role == null) throw new NoSuchElementException("角色不存在: " + id);
        RoleDefinition updated = role.withPermissions(perms);
        fallbackStore.put(id, updated);
        return updated;
    }

    public RoleDefinition assignMenus(String id, Set<String> menus) {
        if (roleMapper != null && roleMenuMapper != null) {
            try {
                RoleDefinitionEntity role = roleMapper.selectById(id);
                if (role == null) throw new NoSuchElementException("角色不存在: " + id);

                QueryWrapper<RoleMenuEntity> mqw = new QueryWrapper<>();
                mqw.eq("role_id", id);
                roleMenuMapper.delete(mqw);

                if (menus != null) {
                    for (String menuId : menus) {
                        roleMenuMapper.insert(new RoleMenuEntity(id, menuId, Instant.now()));
                    }
                }
                return toDomain(role);
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to assign menus to role {} in database: {}, using memory store", id, e.getMessage());
            }
        }

        RoleDefinition role = fallbackStore.get(id);
        if (role == null) throw new NoSuchElementException("角色不存在: " + id);
        RoleDefinition updated = role.withMenus(menus);
        fallbackStore.put(id, updated);
        return updated;
    }

    private RoleDefinition toDomain(RoleDefinitionEntity entity) {
        Set<String> perms = Set.of();
        if (rolePermissionMapper != null) {
            try {
                QueryWrapper<RolePermissionEntity> pqw = new QueryWrapper<>();
                pqw.eq("role_id", entity.getId());
                perms = rolePermissionMapper.selectList(pqw).stream()
                        .map(RolePermissionEntity::getPermissionCode)
                        .collect(Collectors.toSet());
            } catch (Exception e) {
                log.warn("Failed to query permissions for role {} from database: {}", entity.getId(), e.getMessage());
            }
        }

        Set<String> menus = Set.of();
        if (roleMenuMapper != null) {
            try {
                QueryWrapper<RoleMenuEntity> mqw = new QueryWrapper<>();
                mqw.eq("role_id", entity.getId());
                menus = roleMenuMapper.selectList(mqw).stream()
                        .map(RoleMenuEntity::getMenuId)
                        .collect(Collectors.toSet());
            } catch (Exception e) {
                log.warn("Failed to query menus for role {} from database: {}", entity.getId(), e.getMessage());
            }
        }

        return new RoleDefinition(
                entity.getId(),
                entity.getTenantId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getDescription(),
                RoleDefinition.DataScope.valueOf(entity.getDataScope()),
                perms,
                menus,
                Boolean.TRUE.equals(entity.getIsSystem()),
                RoleDefinition.Status.valueOf(entity.getStatus()),
                entity.getCreatedAt()
        );
    }
}
