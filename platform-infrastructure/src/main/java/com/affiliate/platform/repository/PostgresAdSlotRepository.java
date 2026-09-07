package com.affiliate.platform.repository;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.entity.AdSlotEntity;
import com.affiliate.platform.mapper.AdSlotMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 MyBatis-Plus 并集成 Guava + Redis 两级缓存的广告位仓储 (MyBatis-Plus AdSlot Repository)
 * <p>
 * 使用 MyBatis-Plus `AdSlotMapper` 完成与关系数据库的映射读写，
 * 读请求优先穿透 Guava L1 与 Redis L2，保证毫秒级底价和规格校验。
 */
@Repository
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class PostgresAdSlotRepository implements com.affiliate.platform.repository.Repository<AdSlot> {

    private final AdSlotMapper mapper;
    private final TwoTierCache<String, AdSlot> cache;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 1000000);

    public PostgresAdSlotRepository(AdSlotMapper mapper, TwoTierCacheManager cacheManager) {
        this.mapper = mapper;
        this.cache = cacheManager.getOrCreate("adslot", AdSlot.class);
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + seq.incrementAndGet();
    }

    @Override
    public AdSlot save(AdSlot s) {
        Instant createdAt = s.createdAt() == null ? Instant.now() : s.createdAt();
        AdSlotEntity entity = new AdSlotEntity(
                s.id(),
                "public",
                s.name(),
                s.width(),
                s.height(),
                s.floorPrice(),
                s.secure(),
                s.active(),
                createdAt
        );

        if (mapper.selectById(s.id()) != null) {
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }

        cache.put(s.id(), s);
        return s;
    }

    @Override
    public Optional<AdSlot> find(String id) {
        AdSlot val = cache.get(id, key -> {
            AdSlotEntity e = mapper.selectById(key);
            return e != null ? toDomain(e) : null;
        });
        return Optional.ofNullable(val);
    }

    @Override
    public List<AdSlot> findAll() {
        QueryWrapper<AdSlotEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at").last("LIMIT 1000");
        List<AdSlotEntity> entities = mapper.selectList(qw);

        List<AdSlot> list = new ArrayList<>(entities.size());
        for (AdSlotEntity e : entities) {
            AdSlot s = toDomain(e);
            cache.put(s.id(), s);
            list.add(s);
        }
        return list;
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
        cache.evict(id);
    }

    private AdSlot toDomain(AdSlotEntity e) {
        return new AdSlot(
                e.getId(),
                e.getName(),
                e.getWidth(),
                e.getHeight(),
                e.getFloorPrice(),
                e.getSecure(),
                e.getActive(),
                e.getCreatedAt()
        );
    }
}
