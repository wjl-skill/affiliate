package com.affiliate.platform.cdp;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.service.NotFoundException;
import com.affiliate.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 客户数据平台确定性身份图谱打通服务 (CDP Identity Mapping Service)
 * <p>
 * 核心职能：
 * 1. 跨触点统一身份打通 (Deterministic Identity Resolution / Stitching)；
 * 2. 档案持久化接入 PostgreSQL `cdp_profile` 与 `cdp_identity_graph` 表；
 * 3. 引入 Guava + Redis 两级缓存，保证毫秒级跨端身份反查 `resolve(identifier)`；
 * 4. 隐私合规生命周期管控（Opt-out 退出与 GDPR 级联物理擦除）。
 */
@Service
public class IdentityMappingService {

    private final EventPublisher events;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TwoTierCache<String, CustomerProfile> profileCache;
    private final TwoTierCache<String, String> idIndexCache;

    // 内存降级容器（在未装配数据库时供单元测试及轻量环境使用）
    private final ConcurrentMap<String, CustomerProfile> localProfiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> localIdentifierIndex = new ConcurrentHashMap<>();

    @Autowired
    public IdentityMappingService(
            EventPublisher events,
            @Autowired(required = false) JdbcTemplate jdbc,
            @Autowired(required = false) ObjectMapper mapper,
            @Autowired(required = false) TwoTierCacheManager cacheManager
    ) {
        this.events = events;
        this.jdbc = jdbc;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.profileCache = cacheManager != null ? cacheManager.getOrCreate("cdp_profile", CustomerProfile.class) : null;
        this.idIndexCache = cacheManager != null ? cacheManager.getOrCreate("cdp_id_map", String.class) : null;
    }

    public IdentityMappingService(EventPublisher events) {
        this(events, null, null, null);
    }

    /**
     * 创建全新的一方客户档案并落库持久化
     */
    public CustomerProfile create(CustomerProfile input) {
        String id = "cdp_profile_" + UUID.randomUUID();
        CustomerProfile profile = new CustomerProfile(
                id,
                input.primaryId(),
                input.identifiers(),
                input.attributes(),
                input.traits(),
                Instant.now(),
                CustomerProfile.Status.ACTIVE,
                Instant.now()
        );
        save(profile);
        events.publish(DomainEvent.create("cdp.profile.created.v1", tenant(), id, profile));
        return profile;
    }

    /**
     * 根据任意渠道标识（手机、邮箱、OpenID 等）反查解析唯一的客户档案（走两级缓存）
     */
    public CustomerProfile resolve(String identifier) {
        String profileId;
        if (idIndexCache != null) {
            profileId = idIndexCache.get(key(identifier), k -> findProfileIdByIdentifier(identifier));
        } else if (jdbc != null) {
            profileId = findProfileIdByIdentifier(identifier);
        } else {
            profileId = localIdentifierIndex.get(key(identifier));
        }

        if (profileId == null) {
            throw new NotFoundException("customerProfile", identifier);
        }

        CustomerProfile profile;
        if (profileCache != null) {
            profile = profileCache.get(key(profileId), k -> findProfileById(profileId));
        } else if (jdbc != null) {
            profile = findProfileById(profileId);
        } else {
            profile = localProfiles.get(key(profileId));
        }

        if (profile == null) {
            throw new NotFoundException("customerProfile", identifier);
        }
        return profile;
    }

    /**
     * 增量合并客户跨触点数据并更新标签
     */
    public CustomerProfile merge(String primaryId, Set<String> identifiers, Map<String, String> attributes, Set<String> traits) {
        CustomerProfile current = current(primaryId);
        if (current.status() != CustomerProfile.Status.ACTIVE) {
            throw new IllegalStateException("profile is not active");
        }
        CustomerProfile updated = current.merge(identifiers, attributes, traits, Instant.now());
        save(updated);
        events.publish(DomainEvent.create("cdp.profile.updated.v1", tenant(), updated.id(), updated));
        return updated;
    }

    /**
     * 列出当前租户名下的所有客户画像档案
     */
    public List<CustomerProfile> list() {
        if (jdbc != null) {
            String sql = "SELECT id, primary_id, status, identifiers, attributes, traits, last_seen_at, created_at " +
                    "FROM cdp_profile WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 1000";
            return jdbc.query(sql, new ProfileRowMapper(), tenant());
        }

        return localProfiles.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tenant() + ":"))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * 用户隐私退出营销定向
     */
    public CustomerProfile optOut(String primaryId) {
        CustomerProfile p = current(primaryId);
        CustomerProfile next = new CustomerProfile(
                p.id(), p.primaryId(), p.identifiers(), p.attributes(), p.traits(),
                p.lastSeenAt(), CustomerProfile.Status.OPTED_OUT, p.createdAt()
        );
        save(next);
        events.publish(DomainEvent.create("cdp.profile.opted_out.v1", tenant(), next.id(), next));
        return next;
    }

    /**
     * 执行 GDPR/CCPA 被遗忘权级联擦除
     */
    public void erase(String primaryId) {
        CustomerProfile profile = current(primaryId);

        if (jdbc != null) {
            jdbc.update("DELETE FROM cdp_profile WHERE id = ?", profile.id());
            jdbc.update("DELETE FROM cdp_identity_graph WHERE profile_id = ?", profile.id());
        } else {
            localProfiles.remove(key(profile.id()));
            profile.identifiers().forEach(identifier -> localIdentifierIndex.remove(key(identifier)));
            localIdentifierIndex.remove(key(profile.primaryId()));
        }

        if (profileCache != null) {
            profileCache.evict(key(profile.id()));
        }
        if (idIndexCache != null) {
            profile.identifiers().forEach(id -> idIndexCache.evict(key(id)));
            idIndexCache.evict(key(profile.primaryId()));
        }

        events.publish(DomainEvent.create("cdp.profile.erased.v1", tenant(), profile.id(), Map.of("primaryId", primaryId)));
    }

    /**
     * 写入档案并原子维护跨渠道反查映射
     */
    private void save(CustomerProfile profile) {
        if (jdbc != null) {
            try {
                String idsJson = mapper.writeValueAsString(profile.identifiers());
                String attrsJson = mapper.writeValueAsString(profile.attributes());
                String traitsJson = mapper.writeValueAsString(profile.traits());

                String sql = "INSERT INTO cdp_profile (id, tenant_id, primary_id, status, identifiers, attributes, traits, last_seen_at, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?) " +
                        "ON CONFLICT (id) DO UPDATE SET " +
                        "status = EXCLUDED.status, " +
                        "identifiers = EXCLUDED.identifiers, " +
                        "attributes = EXCLUDED.attributes, " +
                        "traits = EXCLUDED.traits, " +
                        "last_seen_at = EXCLUDED.last_seen_at";

                jdbc.update(sql,
                        profile.id(),
                        tenant(),
                        profile.primaryId(),
                        profile.status().name(),
                        idsJson,
                        attrsJson,
                        traitsJson,
                        Timestamp.from(profile.lastSeenAt()),
                        Timestamp.from(profile.createdAt())
                );

                // 维护主标识反查
                saveGraphEntry(profile.primaryId(), profile.id());
                // 维护各渠道标识反查
                for (String id : profile.identifiers()) {
                    saveGraphEntry(id, profile.id());
                }
            } catch (Exception e) {
                throw new IllegalStateException("cannot persist customer profile", e);
            }
        } else {
            // 内存模式防重绑定校验
            profile.identifiers().forEach(identifier -> {
                String existing = localIdentifierIndex.get(key(identifier));
                if (existing != null && !existing.equals(profile.id())) {
                    throw new IllegalArgumentException("identifier already mapped");
                }
            });
            String existingPrimary = localIdentifierIndex.get(key(profile.primaryId()));
            if (existingPrimary != null && !existingPrimary.equals(profile.id())) {
                throw new IllegalArgumentException("primaryId already mapped");
            }

            localProfiles.put(key(profile.id()), profile);
            profile.identifiers().forEach(identifier -> localIdentifierIndex.put(key(identifier), profile.id()));
            localIdentifierIndex.put(key(profile.primaryId()), profile.id());
        }

        // 刷新缓存
        if (profileCache != null) {
            profileCache.put(key(profile.id()), profile);
        }
        if (idIndexCache != null) {
            idIndexCache.put(key(profile.primaryId()), profile.id());
            profile.identifiers().forEach(id -> idIndexCache.put(key(id), profile.id()));
        }
    }

    private void saveGraphEntry(String identifierVal, String profileId) {
        String type = "GENERIC";
        if (identifierVal.contains(":")) {
            type = identifierVal.substring(0, identifierVal.indexOf(':')).toUpperCase();
        }
        String sql = "INSERT INTO cdp_identity_graph (tenant_id, identifier_type, identifier_val, profile_id, linked_at) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, identifier_type, identifier_val) DO UPDATE SET profile_id = EXCLUDED.profile_id";
        jdbc.update(sql, tenant(), type, identifierVal, profileId, Timestamp.from(Instant.now()));
    }

    private String findProfileIdByIdentifier(String identifierVal) {
        if (jdbc == null) return null;
        String sql = "SELECT profile_id FROM cdp_identity_graph WHERE tenant_id = ? AND identifier_val = ?";
        List<String> list = jdbc.queryForList(sql, String.class, tenant(), identifierVal);
        return list.isEmpty() ? null : list.get(0);
    }

    private CustomerProfile findProfileById(String id) {
        if (jdbc == null) return null;
        String sql = "SELECT id, primary_id, status, identifiers, attributes, traits, last_seen_at, created_at FROM cdp_profile WHERE id = ?";
        List<CustomerProfile> results = jdbc.query(sql, new ProfileRowMapper(), id);
        return results.isEmpty() ? null : results.get(0);
    }

    private CustomerProfile current(String primaryId) {
        if (jdbc != null) {
            String sql = "SELECT id, primary_id, status, identifiers, attributes, traits, last_seen_at, created_at " +
                    "FROM cdp_profile WHERE tenant_id = ? AND primary_id = ?";
            List<CustomerProfile> list = jdbc.query(sql, new ProfileRowMapper(), tenant(), primaryId);
            if (!list.isEmpty()) {
                return list.get(0);
            }
        } else {
            Optional<CustomerProfile> found = localProfiles.values().stream()
                    .filter(p -> p.primaryId().equals(primaryId) && localProfiles.containsKey(key(p.id())))
                    .findFirst();
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new NotFoundException("customerProfile", primaryId);
    }

    private static String key(String value) {
        return tenant() + ":" + value;
    }

    private static String tenant() {
        return TenantContext.get() == null ? "public" : TenantContext.required();
    }

    private class ProfileRowMapper implements RowMapper<CustomerProfile> {
        @Override
        public CustomerProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
            Set<String> ids;
            Map<String, String> attrs;
            Set<String> traits;
            try {
                ids = mapper.readValue(rs.getString("identifiers"), new TypeReference<>() {});
            } catch (Exception e) {
                ids = Collections.emptySet();
            }
            try {
                attrs = mapper.readValue(rs.getString("attributes"), new TypeReference<>() {});
            } catch (Exception e) {
                attrs = Collections.emptyMap();
            }
            try {
                traits = mapper.readValue(rs.getString("traits"), new TypeReference<>() {});
            } catch (Exception e) {
                traits = Collections.emptySet();
            }

            return new CustomerProfile(
                    rs.getString("id"),
                    rs.getString("primary_id"),
                    ids,
                    attrs,
                    traits,
                    rs.getTimestamp("last_seen_at").toInstant(),
                    CustomerProfile.Status.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
