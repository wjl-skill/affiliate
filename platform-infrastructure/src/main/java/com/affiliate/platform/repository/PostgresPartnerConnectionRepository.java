package com.affiliate.platform.repository;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.entity.PartnerConnectionEntity;
import com.affiliate.platform.mapper.PartnerConnectionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 MyBatis-Plus 并集成 Guava + Redis 两级缓存的外部连接仓储 (MyBatis-Plus Partner Repository)
 * <p>
 * 采用 MyBatis-Plus `PartnerConnectionMapper` 实现持久化及 JSONB 配置映射。
 */
@Repository
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class PostgresPartnerConnectionRepository implements com.affiliate.platform.repository.Repository<PartnerConnection> {

    private final PartnerConnectionMapper mapper;
    private final ObjectMapper objectMapper;
    private final TwoTierCache<String, PartnerConnection> cache;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 1000000);

    public PostgresPartnerConnectionRepository(
            PartnerConnectionMapper mapper,
            ObjectMapper objectMapper,
            TwoTierCacheManager cacheManager
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.cache = cacheManager.getOrCreate("partner", PartnerConnection.class);
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + seq.incrementAndGet();
    }

    @Override
    public PartnerConnection save(PartnerConnection p) {
        String settingsJson;
        try {
            settingsJson = objectMapper.writeValueAsString(p.settings() == null ? Map.of() : p.settings());
        } catch (Exception e) {
            settingsJson = "{}";
        }

        Instant updatedAt = p.updatedAt() == null ? Instant.now() : p.updatedAt();
        PartnerConnectionEntity entity = new PartnerConnectionEntity(
                p.id(),
                "public",
                p.name(),
                p.type().name(),
                p.endpoint(),
                settingsJson,
                p.status().name(),
                updatedAt
        );

        if (mapper.selectById(p.id()) != null) {
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }

        cache.put(p.id(), p);
        return p;
    }

    @Override
    public Optional<PartnerConnection> find(String id) {
        PartnerConnection val = cache.get(id, key -> {
            PartnerConnectionEntity e = mapper.selectById(key);
            return e != null ? toDomain(e) : null;
        });
        return Optional.ofNullable(val);
    }

    @Override
    public List<PartnerConnection> findAll() {
        QueryWrapper<PartnerConnectionEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("updated_at").last("LIMIT 1000");
        List<PartnerConnectionEntity> entities = mapper.selectList(qw);

        List<PartnerConnection> list = new ArrayList<>(entities.size());
        for (PartnerConnectionEntity e : entities) {
            PartnerConnection p = toDomain(e);
            cache.put(p.id(), p);
            list.add(p);
        }
        return list;
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
        cache.evict(id);
    }

    private PartnerConnection toDomain(PartnerConnectionEntity e) {
        Map<String, String> settings;
        try {
            settings = objectMapper.readValue(e.getSettings(), new TypeReference<>() {});
        } catch (Exception ex) {
            settings = Collections.emptyMap();
        }

        return new PartnerConnection(
                e.getId(),
                e.getName(),
                SupplyType.valueOf(e.getType()),
                e.getEndpoint(),
                settings,
                ConnectionStatus.valueOf(e.getStatus()),
                e.getUpdatedAt()
        );
    }
}
