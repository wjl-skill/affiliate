package com.affiliate.platform.tenant;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.entity.TenantEntity;
import com.affiliate.platform.mapper.TenantMapper;
import com.affiliate.platform.repository.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 MyBatis-Plus 并集成 Guava + Redis 两级缓存的租户仓储 (MyBatis-Plus Tenant Repository)
 * <p>
 * 对应数据库表 `tenant`，使用 `TenantMapper` 进行 ORM 读写，并结合两级缓存保障租户隔离元数据的极速加载。
 */
@org.springframework.stereotype.Repository("tenantRepository")
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class PostgresTenantRepository implements Repository<Tenant> {

    private final TenantMapper mapper;
    private final TwoTierCache<String, Tenant> cache;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 1000000);

    public PostgresTenantRepository(TenantMapper mapper, TwoTierCacheManager cacheManager) {
        this.mapper = mapper;
        this.cache = cacheManager.getOrCreate("tenant", Tenant.class);
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + seq.incrementAndGet();
    }

    @Override
    public Tenant save(Tenant t) {
        Instant createdAt = t.createdAt() == null ? Instant.now() : t.createdAt();
        TenantEntity entity = new TenantEntity(t.id(), t.name(), t.status().name(), createdAt);

        if (mapper.selectById(t.id()) != null) {
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }

        cache.put(t.id(), t);
        return t;
    }

    @Override
    public Optional<Tenant> find(String id) {
        Tenant val = cache.get(id, key -> {
            TenantEntity entity = mapper.selectById(key);
            return entity != null ? toDomain(entity) : null;
        });
        return Optional.ofNullable(val);
    }

    @Override
    public List<Tenant> findAll() {
        QueryWrapper<TenantEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at").last("LIMIT 1000");
        List<TenantEntity> entities = mapper.selectList(qw);

        List<Tenant> list = new ArrayList<>(entities.size());
        for (TenantEntity e : entities) {
            Tenant t = toDomain(e);
            cache.put(t.id(), t);
            list.add(t);
        }
        return list;
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
        cache.evict(id);
    }

    private Tenant toDomain(TenantEntity e) {
        return new Tenant(
                e.getId(),
                e.getName(),
                Tenant.Status.valueOf(e.getStatus()),
                e.getCreatedAt()
        );
    }
}
