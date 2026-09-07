package com.affiliate.platform.repository;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.entity.CreativeEntity;
import com.affiliate.platform.mapper.CreativeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 MyBatis-Plus 并集成 Guava + Redis 两级缓存的广告物料仓储 (MyBatis-Plus Creative Repository)
 * <p>
 * 采用三级架构：
 * 1. 读：Guava L1 (< 1µs) -> Redis L2 (1~2ms) -> MyBatis-Plus `CreativeMapper.selectById`；
 * 2. 写：MyBatis-Plus `insert` / `updateById` 持久化 -> 同步刷新/淘汰两级缓存。
 */
@Repository
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class PostgresCreativeRepository implements com.affiliate.platform.repository.Repository<Creative> {

    private final CreativeMapper mapper;
    private final ObjectMapper objectMapper;
    private final TwoTierCache<String, Creative> cache;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 1000000);

    public PostgresCreativeRepository(
            CreativeMapper mapper,
            ObjectMapper objectMapper,
            TwoTierCacheManager cacheManager
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.cache = cacheManager.getOrCreate("creative", Creative.class);
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + seq.incrementAndGet();
    }

    @Override
    public Creative save(Creative c) {
        String categoriesJson;
        try {
            categoriesJson = objectMapper.writeValueAsString(c.categories() == null ? Set.of() : c.categories());
        } catch (Exception e) {
            categoriesJson = "[]";
        }

        Instant createdAt = c.createdAt() == null ? Instant.now() : c.createdAt();
        CreativeEntity entity = new CreativeEntity(
                c.id(),
                "public",
                c.name(),
                c.type().name(),
                c.assetUrl(),
                c.landingUrl(),
                c.width(),
                c.height(),
                categoriesJson,
                c.active(),
                createdAt
        );

        if (mapper.selectById(c.id()) != null) {
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }

        cache.put(c.id(), c);
        return c;
    }

    @Override
    public Optional<Creative> find(String id) {
        Creative val = cache.get(id, key -> {
            CreativeEntity e = mapper.selectById(key);
            return e != null ? toDomain(e) : null;
        });
        return Optional.ofNullable(val);
    }

    @Override
    public List<Creative> findAll() {
        QueryWrapper<CreativeEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at").last("LIMIT 1000");
        List<CreativeEntity> entities = mapper.selectList(qw);

        List<Creative> list = new ArrayList<>(entities.size());
        for (CreativeEntity e : entities) {
            Creative c = toDomain(e);
            cache.put(c.id(), c);
            list.add(c);
        }
        return list;
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
        cache.evict(id);
    }

    private Creative toDomain(CreativeEntity e) {
        Set<String> categories;
        try {
            categories = objectMapper.readValue(e.getCategories(), new TypeReference<>() {});
        } catch (Exception ex) {
            categories = Collections.emptySet();
        }

        return new Creative(
                e.getId(),
                e.getName(),
                CreativeType.valueOf(e.getType()),
                e.getAssetUrl(),
                e.getLandingUrl(),
                e.getWidth(),
                e.getHeight(),
                categories,
                e.getActive(),
                e.getCreatedAt()
        );
    }
}
