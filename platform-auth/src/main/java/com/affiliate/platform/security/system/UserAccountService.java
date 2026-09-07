package com.affiliate.platform.security.system;

import com.affiliate.platform.entity.UserAccountEntity;
import com.affiliate.platform.entity.UserRoleEntity;
import com.affiliate.platform.mapper.UserAccountMapper;
import com.affiliate.platform.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 用户账号管理服务 (User Account Management Service - MyBatis-Plus)
 * <p>
 * 全面基于 MyBatis-Plus 接入 PostgreSQL 真实持久化存储，操作表 `sys_user_account` 与 `sys_user_role`。
 */
@Service
public class UserAccountService {

    private final UserAccountMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final ConcurrentMap<String, UserAccount> fallbackStore = new ConcurrentHashMap<>();

    public UserAccountService() {
        this(null, null);
    }

    @Autowired
    public UserAccountService(
            @Autowired(required = false) UserAccountMapper userMapper,
            @Autowired(required = false) UserRoleMapper userRoleMapper
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        ensureSeedData();
    }

    private void ensureSeedData() {
        if (userMapper != null) {
            try {
                Long count = userMapper.selectCount(null);
                if (count != null && count == 0) {
                    initDefaultSeedUsers();
                }
            } catch (Exception e) {
                initDefaultSeedUsers();
            }
        } else {
            initDefaultSeedUsers();
        }
    }

    private void initDefaultSeedUsers() {
        saveUser(new UserAccount(
                "usr-admin-01",
                "public",
                "admin",
                "Super Administrator",
                "admin@affiliate.io",
                "+1-800-555-0199",
                "https://api.dicebear.com/7.x/bottts/svg?seed=admin",
                UserAccount.Status.ACTIVE,
                Set.of("SUPER_ADMIN"),
                Instant.now(),
                Instant.now(),
                Instant.now()
        ));

        saveUser(new UserAccount(
                "usr-bd-01",
                "tenant-1",
                "alex_bd",
                "Alex Wang (商务主管)",
                "alex@affiliate.io",
                "+1-800-555-0123",
                "https://api.dicebear.com/7.x/bottts/svg?seed=alex",
                UserAccount.Status.ACTIVE,
                Set.of("AFFILIATE_MANAGER"),
                Instant.now().minusSeconds(3600 * 2),
                Instant.now(),
                Instant.now()
        ));

        saveUser(new UserAccount(
                "usr-fn-01",
                "tenant-1",
                "sarah_fin",
                "Sarah Lee (财务总监)",
                "sarah@affiliate.io",
                "+1-800-555-0188",
                "https://api.dicebear.com/7.x/bottts/svg?seed=sarah",
                UserAccount.Status.ACTIVE,
                Set.of("FINANCE_OFFICER"),
                Instant.now().minusSeconds(3600 * 12),
                Instant.now(),
                Instant.now()
        ));
    }

    public UserAccount saveUser(UserAccount user) {
        if (userMapper != null) {
            UserAccountEntity entity = new UserAccountEntity(
                    user.id(),
                    user.tenantId(),
                    user.username(),
                    user.displayName(),
                    user.email(),
                    user.phone(),
                    user.avatar(),
                    null,
                    user.status().name(),
                    user.lastLoginAt(),
                    user.createdAt() != null ? user.createdAt() : Instant.now(),
                    Instant.now()
            );

            if (userMapper.selectById(user.id()) != null) {
                userMapper.updateById(entity);
            } else {
                userMapper.insert(entity);
            }

            if (userRoleMapper != null && user.roles() != null) {
                assignRoles(user.id(), user.roles());
            }
            return user;
        }

        fallbackStore.put(user.id(), user);
        return user;
    }

    public List<UserAccount> listUsers() {
        if (userMapper != null) {
            QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("created_at").last("LIMIT 1000");
            List<UserAccountEntity> entities = userMapper.selectList(qw);
            return entities.stream().map(this::toDomain).toList();
        }
        return new ArrayList<>(fallbackStore.values());
    }

    public Optional<UserAccount> find(String id) {
        if (userMapper != null) {
            UserAccountEntity entity = userMapper.selectById(id);
            return Optional.ofNullable(entity).map(this::toDomain);
        }
        return Optional.ofNullable(fallbackStore.get(id));
    }

    public Optional<UserAccount> findByUsername(String username) {
        if (userMapper != null) {
            QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
            qw.eq("username", username).last("LIMIT 1");
            UserAccountEntity entity = userMapper.selectOne(qw);
            return Optional.ofNullable(entity).map(this::toDomain);
        }
        return fallbackStore.values().stream()
                .filter(u -> u.username().equalsIgnoreCase(username))
                .findFirst();
    }

    public UserAccount toggleStatus(String id, UserAccount.Status status) {
        if (userMapper != null) {
            UserAccountEntity entity = userMapper.selectById(id);
            if (entity == null) throw new NoSuchElementException("用户不存在: " + id);
            entity.setStatus(status.name());
            entity.setUpdatedAt(Instant.now());
            userMapper.updateById(entity);
            return toDomain(entity);
        }
        UserAccount user = fallbackStore.get(id);
        if (user == null) throw new NoSuchElementException("用户不存在: " + id);
        UserAccount updated = user.withStatus(status);
        fallbackStore.put(id, updated);
        return updated;
    }

    public UserAccount assignRoles(String id, Set<String> roles) {
        if (userMapper != null && userRoleMapper != null) {
            UserAccountEntity entity = userMapper.selectById(id);
            if (entity == null) throw new NoSuchElementException("用户不存在: " + id);

            QueryWrapper<UserRoleEntity> delQw = new QueryWrapper<>();
            delQw.eq("user_id", id);
            userRoleMapper.delete(delQw);

            if (roles != null) {
                for (String roleId : roles) {
                    userRoleMapper.insert(new UserRoleEntity(id, roleId, Instant.now()));
                }
            }
            return toDomain(entity);
        }
        UserAccount user = fallbackStore.get(id);
        if (user == null) throw new NoSuchElementException("用户不存在: " + id);
        UserAccount updated = user.withRoles(roles);
        fallbackStore.put(id, updated);
        return updated;
    }

    public boolean delete(String id) {
        if (userMapper != null) {
            UserAccountEntity entity = userMapper.selectById(id);
            if (entity != null && "admin".equalsIgnoreCase(entity.getUsername())) {
                throw new IllegalArgumentException("系统超级管理员不可删除！");
            }
            if (userRoleMapper != null) {
                QueryWrapper<UserRoleEntity> delQw = new QueryWrapper<>();
                delQw.eq("user_id", id);
                userRoleMapper.delete(delQw);
            }
            return userMapper.deleteById(id) > 0;
        }
        UserAccount user = fallbackStore.get(id);
        if (user != null && "admin".equalsIgnoreCase(user.username())) {
            throw new IllegalArgumentException("系统超级管理员不可删除！");
        }
        return fallbackStore.remove(id) != null;
    }

    private UserAccount toDomain(UserAccountEntity entity) {
        Set<String> roleCodes = Set.of();
        if (userRoleMapper != null) {
            QueryWrapper<UserRoleEntity> roleQw = new QueryWrapper<>();
            roleQw.eq("user_id", entity.getId());
            List<UserRoleEntity> roleEntities = userRoleMapper.selectList(roleQw);
            roleCodes = roleEntities.stream().map(UserRoleEntity::getRoleId).collect(Collectors.toSet());
        }

        return new UserAccount(
                entity.getId(),
                entity.getTenantId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getAvatar(),
                UserAccount.Status.valueOf(entity.getStatus()),
                roleCodes,
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
