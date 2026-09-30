package com.affiliate.platform.dmp;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.entity.AudienceSegmentEntity;
import com.affiliate.platform.entity.DmpSegmentMemberEntity;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.mapper.AudienceSegmentMapper;
import com.affiliate.platform.mapper.DmpSegmentMemberMapper;
import com.affiliate.platform.service.NotFoundException;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** DMP 受众分群服务，生产环境的读写统一通过 MyBatis-Plus Mapper。 */
@Service
public class DmpService {
    private final EventPublisher events;
    private final AudienceSegmentMapper segmentMapper;
    private final DmpSegmentMemberMapper memberMapper;
    private final StringRedisTemplate redisTemplate;
    private final TwoTierCache<String, AudienceSegment> segmentCache;
    private final ConcurrentMap<String, AudienceSegment> localSegments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> localMembers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, java.util.BitSet> localBitmaps = new ConcurrentHashMap<>();

    @Autowired
    public DmpService(EventPublisher events,
                      @Autowired(required = false) AudienceSegmentMapper segmentMapper,
                      @Autowired(required = false) DmpSegmentMemberMapper memberMapper,
                      @Autowired(required = false) StringRedisTemplate redisTemplate,
                      @Autowired(required = false) TwoTierCacheManager cacheManager) {
        this.events = events;
        this.segmentMapper = segmentMapper;
        this.memberMapper = memberMapper;
        this.redisTemplate = redisTemplate;
        this.segmentCache = cacheManager == null ? null : cacheManager.getOrCreate("dmp_segment", AudienceSegment.class);
    }

    public DmpService(EventPublisher events) { this(events, null, null, null, null); }

    public AudienceSegment create(AudienceSegment input) {
        String id = "dmp_seg_" + UUID.randomUUID();
        AudienceSegment value = new AudienceSegment(id, input.name(), input.source(), input.taxonomy(), input.expiresAt(), 0,
                AudienceSegment.Status.DRAFT, Instant.now());
        if (segmentMapper != null) segmentMapper.insert(toEntity(value));
        else {
            localSegments.put(key(id), value);
            localMembers.put(key(id), ConcurrentHashMap.newKeySet());
            localBitmaps.put(key(id), new java.util.BitSet());
        }
        cache(value);
        events.publish(DomainEvent.create("dmp.segment.created.v1", tenant(), id, value));
        return value;
    }

    public List<AudienceSegment> list() {
        if (segmentMapper != null) {
            List<AudienceSegment> result = segmentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AudienceSegmentEntity>()
                    .eq(AudienceSegmentEntity::getTenantId, tenant()).orderByDesc(AudienceSegmentEntity::getCreatedAt))
                    .stream().map(DmpService::fromEntity).toList();
            result.forEach(this::cache);
            return result;
        }
        String prefix = tenant() + ":";
        return localSegments.entrySet().stream().filter(e -> e.getKey().startsWith(prefix)).map(Map.Entry::getValue)
                .sorted(Comparator.comparing(AudienceSegment::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    public AudienceSegment get(String id) {
        String cacheKey = key(id);
        if (segmentCache != null) {
            AudienceSegment cached = segmentCache.get(cacheKey, ignored -> findInDb(id));
            if (cached != null) return cached;
        }
        AudienceSegment value = segmentMapper != null ? findInDb(id) : localSegments.get(cacheKey);
        if (value == null) throw new NotFoundException("dmpSegment", id);
        return value;
    }

    private AudienceSegment findInDb(String id) {
        if (segmentMapper == null) return null;
        AudienceSegmentEntity entity = segmentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AudienceSegmentEntity>()
                .eq(AudienceSegmentEntity::getId, id).eq(AudienceSegmentEntity::getTenantId, tenant()));
        return entity == null ? null : fromEntity(entity);
    }

    public AudienceSegment activate(String id, boolean active) {
        AudienceSegment updated = get(id).activate(active);
        if (segmentMapper != null) {
            if (segmentMapper.updateById(toEntity(updated)) == 0) throw new NotFoundException("dmpSegment", id);
        } else localSegments.put(key(id), updated);
        cache(updated);
        return updated;
    }

    public AudienceSegment addMembers(String id, Set<String> anonymousIds) {
        if (anonymousIds == null || anonymousIds.isEmpty() || anonymousIds.stream().anyMatch(v -> v == null || v.isBlank()))
            throw new IllegalArgumentException("anonymousIds must not be blank");
        AudienceSegment current = get(id);
        long newCount;
        if (segmentMapper != null && memberMapper != null) {
            anonymousIds.forEach(aid -> memberMapper.insertIgnore(new DmpSegmentMemberEntity(id, aid)));
            Long count = memberMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DmpSegmentMemberEntity>()
                    .eq(DmpSegmentMemberEntity::getSegmentId, id));
            newCount = count == null ? 0 : count;
            segmentMapper.updateById(toEntity(new AudienceSegment(current.id(), current.name(), current.source(), current.taxonomy(), current.expiresAt(), newCount, current.status(), current.createdAt())));
        } else {
            Set<String> set = localMembers.computeIfAbsent(key(id), ignored -> ConcurrentHashMap.newKeySet());
            set.addAll(anonymousIds);
            newCount = set.size();
        }

        // 工业级位图优化：将匿名标识哈希为整数 bit 偏移量，写入 Redis Bitmap 与本地 BitSet
        java.util.BitSet bs = localBitmaps.computeIfAbsent(key(id), k -> new java.util.BitSet());
        for (String aid : anonymousIds) {
            long offset = toBitOffset(aid);
            synchronized (bs) {
                bs.set((int) offset);
            }
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().setBit("dmp:seg:bitmap:" + id, offset, true);
                } catch (Exception ignored) {}
            }
        }

        // 保持向后兼容性双写
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForSet().add("dmp:seg:members:" + id, anonymousIds.toArray(new String[0]));
            } catch (Exception ignored) {}
        }

        AudienceSegment updated = new AudienceSegment(current.id(), current.name(), current.source(), current.taxonomy(), current.expiresAt(), newCount, current.status(), current.createdAt());
        if (segmentMapper == null) localSegments.put(key(id), updated);
        cache(updated);
        events.publish(DomainEvent.create("dmp.segment.members_updated.v1", tenant(), id, updated.memberCount()));
        return updated;
    }

    public boolean contains(String id, String anonymousId) {
        if (!get(id).validAt(Instant.now())) return false;
        long offset = toBitOffset(anonymousId);

        // 1. 优先通过微秒级 Redis Bitmap 命中判断 (单次 O(1) < 0.1ms，内存极度压缩)
        if (redisTemplate != null) {
            try {
                Boolean isBitSet = redisTemplate.opsForValue().getBit("dmp:seg:bitmap:" + id, offset);
                if (Boolean.TRUE.equals(isBitSet)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 2. 本地内存 BitSet 命中 (纳秒级)
        java.util.BitSet bs = localBitmaps.get(key(id));
        if (bs != null) {
            synchronized (bs) {
                if (bs.get((int) offset)) return true;
            }
        }

        // 3. 兼容旧版本 Redis Set
        if (redisTemplate != null) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("dmp:seg:members:" + id, anonymousId))) return true;
            } catch (Exception ignored) {}
        }

        // 4. 数据库持久化兜底与本地内存 Set 兜底
        if (memberMapper != null) return memberMapper.countByMember(id, anonymousId) > 0;
        return localMembers.getOrDefault(key(id), Set.of()).contains(anonymousId);
    }

    /**
     * 将匿名字符串标识确定性映射为 0 ~ 99,999,999 内的正整数位偏移量
     */
    public static long toBitOffset(String anonymousId) {
        if (anonymousId == null) return 0L;
        long h = 1125899906842597L;
        for (int i = 0; i < anonymousId.length(); i++) {
            h = 31L * h + anonymousId.charAt(i);
        }
        return Math.abs(h % 100_000_000L);
    }

    private void cache(AudienceSegment value) { if (segmentCache != null) segmentCache.put(key(value.id()), value); }
    private static String key(String id) { return tenant() + ":" + id; }
    private static String tenant() { return TenantContext.get() == null ? "public" : TenantContext.required(); }

    private AudienceSegmentEntity toEntity(AudienceSegment value) {
        AudienceSegmentEntity e = new AudienceSegmentEntity();
        e.setId(value.id()); e.setTenantId(tenant()); e.setName(value.name()); e.setSource(value.source().name());
        e.setTaxonomy(String.join(",", value.taxonomy())); e.setMemberCount(value.memberCount()); e.setStatus(value.status().name());
        e.setExpiresAt(value.expiresAt()); e.setCreatedAt(value.createdAt()); return e;
    }

    private static AudienceSegment fromEntity(AudienceSegmentEntity e) {
        Set<String> taxonomy = e.getTaxonomy() == null || e.getTaxonomy().isBlank() ? Set.of() : Set.of(e.getTaxonomy().split(","));
        return new AudienceSegment(e.getId(), e.getName(), AudienceSegment.Source.valueOf(e.getSource()), taxonomy, e.getExpiresAt(),
                e.getMemberCount() == null ? 0 : e.getMemberCount(), AudienceSegment.Status.valueOf(e.getStatus()), e.getCreatedAt());
    }
}
