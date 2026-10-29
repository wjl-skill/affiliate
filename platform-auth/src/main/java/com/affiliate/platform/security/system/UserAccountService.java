package com.affiliate.platform.security.system;

import com.affiliate.platform.entity.UserAccountEntity;
import com.affiliate.platform.entity.UserRoleEntity;
import com.affiliate.platform.mapper.UserAccountMapper;
import com.affiliate.platform.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 用户账号管理服务 (User Account Management Service - MyBatis-Plus)
 * <p>
 * 全面基于 MyBatis-Plus 接入 PostgreSQL 真实持久化存储，操作表 `sys_user_account` 与 `sys_user_role`，
 * 并具备离线内存回退保护能力。密码以 BCrypt 哈希存储于 `password_hash` 列。
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    /** 新建用户与历史无密码种子用户的统一初始口令 */
    public static final String DEFAULT_INITIAL_PASSWORD = "Admin@123";

    private final UserAccountMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final ConcurrentMap<String, UserAccount> fallbackStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> fallbackPasswordHashes = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
        ensureInitialPasswords();
    }

    private void ensureSeedData() {
        if (userMapper != null) {
            try {
                Long count = userMapper.selectCount(null);
                if (count != null && count == 0) {
                    initDefaultSeedUsers();
                }
            } catch (Exception e) {
                log.warn("Failed to check database seed users, using memory fallback: {}", e.getMessage());
                initDefaultSeedUsersFallbackOnly();
            }
        } else {
            initDefaultSeedUsersFallbackOnly();
        }
    }

    private void initDefaultSeedUsers() {
        for (UserAccount seed : buildSeedUsers()) {
            saveUser(seed);
        }
    }

    private void initDefaultSeedUsersFallbackOnly() {
        for (UserAccount seed : buildSeedUsers()) {
            fallbackStore.put(seed.id(), seed);
            fallbackPasswordHashes.putIfAbsent(seed.username().toLowerCase(), passwordEncoder.encode(DEFAULT_INITIAL_PASSWORD));
        }
    }

    private List<UserAccount> buildSeedUsers() {
        return List.of(
                new UserAccount(
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
                ),
                new UserAccount(
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
                ),
                new UserAccount(
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
                )
        );
    }

    public UserAccount saveUser(UserAccount user) {
        fallbackStore.put(user.id(), user);
        fallbackPasswordHashes.putIfAbsent(user.username().toLowerCase(), passwordEncoder.encode(DEFAULT_INITIAL_PASSWORD));

        if (userMapper != null) {
            try {
                UserAccountEntity existing = userMapper.selectById(user.id());
                String carriedHash = existing != null ? existing.getPasswordHash() : null;
                if (carriedHash == null) {
                    carriedHash = findPasswordHash(user.username());
                }
                if (carriedHash == null) {
                    carriedHash = passwordEncoder.encode(DEFAULT_INITIAL_PASSWORD);
                }

                UserAccountEntity entity = new UserAccountEntity(
                        user.id(),
                        user.tenantId(),
                        user.username(),
                        user.displayName(),
                        user.email(),
                        user.phone(),
                        user.avatar(),
                        carriedHash,
                        user.status().name(),
                        user.lastLoginAt(),
                        user.createdAt() != null ? user.createdAt() : Instant.now(),
                        Instant.now()
                );

                if (existing != null) {
                    userMapper.updateById(entity);
                } else {
                    userMapper.insert(entity);
                }

                if (userRoleMapper != null && user.roles() != null) {
                    assignRoles(user.id(), user.roles());
                }
            } catch (Exception e) {
                log.warn("Failed to persist user {} to database: {}, using memory store", user.id(), e.getMessage());
            }
        }
        return user;
    }

    public List<UserAccount> listUsers() {
        if (userMapper != null) {
            try {
                QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
                qw.orderByDesc("created_at").last("LIMIT 1000");
                List<UserAccountEntity> entities = userMapper.selectList(qw);
                return entities.stream().map(this::toDomain).toList();
            } catch (Exception e) {
                log.warn("Failed to query user list from database: {}, using memory store", e.getMessage());
            }
        }
        return new ArrayList<>(fallbackStore.values());
    }

    public Optional<UserAccount> find(String id) {
        if (userMapper != null) {
            try {
                UserAccountEntity entity = userMapper.selectById(id);
                return Optional.ofNullable(entity).map(this::toDomain);
            } catch (Exception e) {
                log.warn("Failed to query user {} from database: {}, using memory store", id, e.getMessage());
            }
        }
        return Optional.ofNullable(fallbackStore.get(id));
    }

    public Optional<UserAccount> findByUsername(String username) {
        if (userMapper != null) {
            try {
                QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
                qw.eq("username", username).last("LIMIT 1");
                UserAccountEntity entity = userMapper.selectOne(qw);
                return Optional.ofNullable(entity).map(this::toDomain);
            } catch (Exception e) {
                log.warn("Failed to query user by username {} from database: {}, using memory store", username, e.getMessage());
            }
        }
        return fallbackStore.values().stream()
                .filter(u -> u.username().equalsIgnoreCase(username))
                .findFirst();
    }

    public Optional<UserAccountEntity> findEntityByUsername(String username, String tenantId) {
        if (userMapper != null) {
            try {
                QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
                qw.eq("username", username);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", tenantId);
                }
                qw.last("LIMIT 1");
                return Optional.ofNullable(userMapper.selectOne(qw));
            } catch (Exception e) {
                log.warn("Failed to query user entity by username {} from database: {}, using memory store", username, e.getMessage());
            }
        }
        UserAccount user = fallbackStore.values().stream()
                .filter(u -> u.username().equalsIgnoreCase(username) && (tenantId == null || tenantId.isBlank() || tenantId.equalsIgnoreCase(u.tenantId())))
                .findFirst()
                .orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        String hash = fallbackPasswordHashes.get(username.toLowerCase());
        return Optional.of(new UserAccountEntity(
                user.id(), user.tenantId(), user.username(), user.displayName(), user.email(), user.phone(),
                user.avatar(), hash, user.status().name(), user.lastLoginAt(), user.createdAt(), user.updatedAt()
        ));
    }

    public UserAccount toggleStatus(String id, UserAccount.Status status) {
        if (userMapper != null) {
            try {
                UserAccountEntity entity = userMapper.selectById(id);
                if (entity == null) throw new NoSuchElementException("用户不存在: " + id);
                entity.setStatus(status.name());
                entity.setUpdatedAt(Instant.now());
                userMapper.updateById(entity);
                return toDomain(entity);
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to toggle user {} status to {} in database: {}, using memory store", id, status, e.getMessage());
            }
        }
        UserAccount user = fallbackStore.get(id);
        if (user == null) throw new NoSuchElementException("用户不存在: " + id);
        UserAccount updated = user.withStatus(status);
        fallbackStore.put(id, updated);
        return updated;
    }

    public UserAccount assignRoles(String id, Set<String> roles) {
        if (userMapper != null && userRoleMapper != null) {
            try {
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
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to assign roles to user {} in database: {}, using memory store", id, e.getMessage());
            }
        }
        UserAccount user = fallbackStore.get(id);
        if (user == null) throw new NoSuchElementException("用户不存在: " + id);
        UserAccount updated = user.withRoles(roles);
        fallbackStore.put(id, updated);
        return updated;
    }

    public boolean delete(String id) {
        if (userMapper != null) {
            try {
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
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to delete user {} from database: {}, using memory store", id, e.getMessage());
            }
        }
        UserAccount user = fallbackStore.get(id);
        if (user != null && "admin".equalsIgnoreCase(user.username())) {
            throw new IllegalArgumentException("系统超级管理员不可删除！");
        }
        return fallbackStore.remove(id) != null;
    }

    /**
     * 查询指定用户名的 BCrypt 密码哈希 (登录校验用)
     */
    public String findPasswordHash(String username) {
        if (username == null || username.isBlank()) return null;
        if (userMapper != null) {
            try {
                QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
                qw.eq("username", username).last("LIMIT 1");
                UserAccountEntity entity = userMapper.selectOne(qw);
                return entity != null ? entity.getPasswordHash() : null;
            } catch (Exception e) {
                log.warn("Failed to query password hash for user {} from database: {}, using memory store", username, e.getMessage());
                return fallbackPasswordHashes.get(username.toLowerCase());
            }
        }
        return fallbackPasswordHashes.get(username.toLowerCase());
    }

    /**
     * 重置指定用户名口令
     */
    public boolean setPassword(String username, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        if (userMapper != null) {
            try {
                QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
                qw.eq("username", username).last("LIMIT 1");
                UserAccountEntity entity = userMapper.selectOne(qw);
                if (entity == null) return false;
                entity.setPasswordHash(hash);
                entity.setUpdatedAt(Instant.now());
                userMapper.updateById(entity);
                return true;
            } catch (Exception e) {
                log.warn("Failed to update password hash for user {} in database: {}, using memory store", username, e.getMessage());
            }
        }
        fallbackPasswordHashes.put(username.toLowerCase(), hash);
        return fallbackStore.values().stream().anyMatch(u -> u.username().equalsIgnoreCase(username));
    }

    public boolean matchesPassword(String username, String rawPassword) {
        String hash = findPasswordHash(username);
        if (hash == null || hash.isBlank()) {
            if (DEFAULT_INITIAL_PASSWORD.equals(rawPassword)) {
                setPassword(username, rawPassword);
                return true;
            }
            return false;
        }
        return rawPassword != null && passwordEncoder.matches(rawPassword, hash);
    }

    /**
     * 登录成功后刷新最近登录时间
     */
    public void recordLogin(String userId) {
        Instant now = Instant.now();
        if (userMapper != null) {
            try {
                UserAccountEntity entity = userMapper.selectById(userId);
                if (entity != null) {
                    entity.setLastLoginAt(now);
                    entity.setUpdatedAt(now);
                    userMapper.updateById(entity);
                }
                return;
            } catch (Exception e) {
                log.warn("Failed to record login for user {} in database: {}, using memory store", userId, e.getMessage());
            }
        }
        UserAccount user = fallbackStore.get(userId);
        if (user != null) {
            fallbackStore.put(userId, user.withLastLogin(now));
        }
    }

    /**
     * 历史种子数据升级兼容：为所有 password_hash 为空的账号写入初始口令
     */
    private void ensureInitialPasswords() {
        try {
            for (UserAccount user : listUsers()) {
                if (findPasswordHash(user.username()) == null) {
                    if (userMapper != null) {
                        QueryWrapper<UserAccountEntity> qw = new QueryWrapper<>();
                        qw.eq("username", user.username()).last("LIMIT 1");
                        UserAccountEntity entity = userMapper.selectOne(qw);
                        if (entity != null) {
                            entity.setPasswordHash(passwordEncoder.encode(DEFAULT_INITIAL_PASSWORD));
                            userMapper.updateById(entity);
                        }
                    } else {
                        fallbackPasswordHashes.put(user.username().toLowerCase(),
                                passwordEncoder.encode(DEFAULT_INITIAL_PASSWORD));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Initial password check completed with notice: {}", e.getMessage());
        }
    }

    private UserAccount toDomain(UserAccountEntity entity) {
        Set<String> roleCodes = Set.of();
        if (userRoleMapper != null) {
            try {
                QueryWrapper<UserRoleEntity> roleQw = new QueryWrapper<>();
                roleQw.eq("user_id", entity.getId());
                List<UserRoleEntity> roleEntities = userRoleMapper.selectList(roleQw);
                roleCodes = roleEntities.stream().map(UserRoleEntity::getRoleId).collect(Collectors.toSet());
            } catch (Exception e) {
                log.warn("Failed to fetch roles for user {} from database: {}", entity.getId(), e.getMessage());
            }
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
