package com.affiliate.platform.dmp;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.service.NotFoundException;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * 数据管理平台业务服务 (DMP Audience Service)
 * <p>
 * 维护面向程序化竞价的匿名受众客群：
 * 1. 客群元数据接入 PostgreSQL `dmp_segment` 表，并结合 Guava + Redis 两级缓存；
 * 2. 匿名成员关系接入 PostgreSQL `dmp_segment_member` 表落盘，同时在 Redis Set 中建立高速索引；
 * 3. 毫秒级受众成员归属校验 (Membership Check)，支持 O(1) Redis 判定与高并发穿透保护。
 */
@Service
public class DmpService {

    private final EventPublisher events;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redisTemplate;
    private final TwoTierCache<String, AudienceSegment> segmentCache;

    // 内存回退容器（在未配置数据库/Redis 时用于本地测试与开发降级）
    private final ConcurrentMap<String, AudienceSegment> localSegments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> localMembers = new ConcurrentHashMap<>();

    @Autowired
    public DmpService(
            EventPublisher events,
            @Autowired(required = false) JdbcTemplate jdbc,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) TwoTierCacheManager cacheManager
    ) {
        this.events = events;
        this.jdbc = jdbc;
        this.redisTemplate = redisTemplate;
        this.segmentCache = cacheManager != null ? cacheManager.getOrCreate("dmp_segment", AudienceSegment.class) : null;
    }

    public DmpService(EventPublisher events) {
        this(events, null, null, null);
    }

    /**
     * 注册创建新受众分群，持久化至 PostgreSQL 并发布 dmp.segment.created.v1 事件
     */
    public AudienceSegment create(AudienceSegment input) {
        String id = "dmp_seg_" + UUID.randomUUID();
        AudienceSegment value = new AudienceSegment(
                id,
                input.name(),
                input.source(),
                input.taxonomy(),
                input.expiresAt(),
                0,
                AudienceSegment.Status.DRAFT,
                Instant.now()
        );

        if (jdbc != null) {
            String sql = "INSERT INTO dmp_segment (id, tenant_id, name, source, taxonomy, member_count, status, expires_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (id) DO UPDATE SET " +
                    "name = EXCLUDED.name, status = EXCLUDED.status, member_count = EXCLUDED.member_count";
            String taxonomyStr = String.join(",", value.taxonomy());
            jdbc.update(sql,
                    id,
                    tenant(),
                    value.name(),
                    value.source().name(),
                    taxonomyStr,
                    value.memberCount(),
                    value.status().name(),
                    Timestamp.from(value.expiresAt()),
                    Timestamp.from(value.createdAt())
            );
        } else {
            localSegments.put(key(id), value);
            localMembers.put(key(id), ConcurrentHashMap.newKeySet());
        }

        if (segmentCache != null) {
            segmentCache.put(key(id), value);
        }

        events.publish(DomainEvent.create("dmp.segment.created.v1", tenant(), id, value));
        return value;
    }

    /**
     * 查询当前租户名下的所有受众分群列表
     */
    public List<AudienceSegment> list() {
        if (jdbc != null) {
            String sql = "SELECT id, name, source, taxonomy, member_count, status, expires_at, created_at " +
                    "FROM dmp_segment WHERE tenant_id = ? ORDER BY created_at DESC";
            List<AudienceSegment> list = jdbc.query(sql, new SegmentRowMapper(), tenant());
            if (segmentCache != null) {
                list.forEach(s -> segmentCache.put(key(s.id()), s));
            }
            return list;
        }

        String prefix = tenant() + ":";
        return localSegments.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * 根据分群 ID 检索分群详情（走 Guava + Redis 两级缓存）
     */
    public AudienceSegment get(String id) {
        String cacheKey = key(id);
        if (segmentCache != null) {
            AudienceSegment cached = segmentCache.get(cacheKey, k -> findInDb(id));
            if (cached != null) return cached;
        }

        if (jdbc != null) {
            AudienceSegment fromDb = findInDb(id);
            if (fromDb != null) return fromDb;
        } else {
            AudienceSegment local = localSegments.get(cacheKey);
            if (local != null) return local;
        }

        throw new NotFoundException("dmpSegment", id);
    }

    private AudienceSegment findInDb(String id) {
        if (jdbc == null) return null;
        String sql = "SELECT id, name, source, taxonomy, member_count, status, expires_at, created_at " +
                "FROM dmp_segment WHERE id = ?";
        List<AudienceSegment> results = jdbc.query(sql, new SegmentRowMapper(), id);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 切换分群的启用状态
     */
    public AudienceSegment activate(String id, boolean active) {
        AudienceSegment current = get(id);
        AudienceSegment updated = current.activate(active);

        if (jdbc != null) {
            jdbc.update("UPDATE dmp_segment SET status = ? WHERE id = ?", updated.status().name(), id);
        } else {
            localSegments.put(key(id), updated);
        }

        if (segmentCache != null) {
            segmentCache.put(key(id), updated);
        }
        return updated;
    }

    /**
     * 向指定受众分群批量追加匿名人群标识 (落盘 PG 并同步 Redis Set)
     */
    public AudienceSegment addMembers(String id, Set<String> anonymousIds) {
        if (anonymousIds == null || anonymousIds.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException("anonymousIds must not be blank");
        }
        AudienceSegment current = get(id);

        long newCount;
        if (jdbc != null) {
            // 批量持久化到 PG
            List<Object[]> batchArgs = anonymousIds.stream()
                    .map(aid -> new Object[]{id, aid})
                    .toList();
            jdbc.batchUpdate("INSERT INTO dmp_segment_member (segment_id, anonymous_id) VALUES (?, ?) ON CONFLICT DO NOTHING", batchArgs);

            // 查询最新成员总数
            Long count = jdbc.queryForObject("SELECT count(*) FROM dmp_segment_member WHERE segment_id = ?", Long.class, id);
            newCount = count != null ? count : current.memberCount() + anonymousIds.size();
            jdbc.update("UPDATE dmp_segment SET member_count = ? WHERE id = ?", newCount, id);
        } else {
            Set<String> set = localMembers.computeIfAbsent(key(id), ignored -> ConcurrentHashMap.newKeySet());
            set.addAll(anonymousIds);
            newCount = set.size();
        }

        // 写入 Redis Set 高速索引
        if (redisTemplate != null) {
            try {
                String redisKey = "dmp:seg:members:" + id;
                redisTemplate.opsForSet().add(redisKey, anonymousIds.toArray(new String[0]));
            } catch (Exception ignored) {}
        }

        AudienceSegment updated = new AudienceSegment(
                current.id(),
                current.name(),
                current.source(),
                current.taxonomy(),
                current.expiresAt(),
                newCount,
                current.status(),
                current.createdAt()
        );

        if (jdbc == null) {
            localSegments.put(key(id), updated);
        }
        if (segmentCache != null) {
            segmentCache.put(key(id), updated);
        }

        events.publish(DomainEvent.create("dmp.segment.members_updated.v1", tenant(), id, updated.memberCount()));
        return updated;
    }

    /**
     * 判定指定匿名用户 ID 是否命中有效分群 (Membership Check)
     */
    public boolean contains(String id, String anonymousId) {
        AudienceSegment seg = get(id);
        if (!seg.validAt(Instant.now())) {
            return false;
        }

        // 优先使用 Redis Set O(1) 判定
        if (redisTemplate != null) {
            try {
                Boolean isMember = redisTemplate.opsForSet().isMember("dmp:seg:members:" + id, anonymousId);
                if (Boolean.TRUE.equals(isMember)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        if (jdbc != null) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM dmp_segment_member WHERE segment_id = ? AND anonymous_id = ?",
                    Integer.class, id, anonymousId);
            return count != null && count > 0;
        }

        return localMembers.getOrDefault(key(id), Set.of()).contains(anonymousId);
    }

    private static String key(String id) {
        return tenant() + ":" + id;
    }

    private static String tenant() {
        return TenantContext.get() == null ? "public" : TenantContext.required();
    }

    private static class SegmentRowMapper implements RowMapper<AudienceSegment> {
        @Override
        public AudienceSegment mapRow(ResultSet rs, int rowNum) throws SQLException {
            String taxStr = rs.getString("taxonomy");
            Set<String> taxonomy = taxStr != null && !taxStr.isBlank()
                    ? Set.of(taxStr.split(","))
                    : Set.of();

            return new AudienceSegment(
                    rs.getString("id"),
                    rs.getString("name"),
                    AudienceSegment.Source.valueOf(rs.getString("source")),
                    taxonomy,
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getLong("member_count"),
                    AudienceSegment.Status.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}
