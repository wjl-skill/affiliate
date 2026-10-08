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
        String currentTenant = com.affiliate.platform.tenant.TenantContext.get();
        String tenantId = (p.tenantId() != null && !p.tenantId().isBlank() && !"public".equals(p.tenantId()))
                ? p.tenantId()
                : (currentTenant != null && !currentTenant.isBlank() ? currentTenant : "public");

        PartnerConnectionEntity entity = new PartnerConnectionEntity(
                p.id(),
                tenantId,
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

        PartnerConnection savedDomain = p.withTenant(tenantId);
        cache.put(tenantId + ":" + p.id(), savedDomain);
        cache.put(p.id(), savedDomain);
        return savedDomain;
    }

    @Override
    public Optional<PartnerConnection> find(String id) {
        String currentTenant = com.affiliate.platform.tenant.TenantContext.get();
        String tenantId = (currentTenant != null && !currentTenant.isBlank()) ? currentTenant : "public";
        String cacheKey = tenantId + ":" + id;

        PartnerConnection val = cache.get(cacheKey, key -> {
            QueryWrapper<PartnerConnectionEntity> qw = new QueryWrapper<>();
            qw.eq("id", id);
            if (currentTenant != null && !currentTenant.isBlank()) {
                qw.and(wrapper -> wrapper.eq("tenant_id", currentTenant).or().eq("tenant_id", "public"));
            }
            PartnerConnectionEntity e = mapper.selectOne(qw.last("LIMIT 1"));
            return e != null ? toDomain(e) : null;
        });
        return Optional.ofNullable(val);
    }

    @Override
    public List<PartnerConnection> findAll() {
        QueryWrapper<PartnerConnectionEntity> qw = new QueryWrapper<>();
        String currentTenant = com.affiliate.platform.tenant.TenantContext.get();
        if (currentTenant != null && !currentTenant.isBlank()) {
            qw.and(wrapper -> wrapper.eq("tenant_id", currentTenant).or().eq("tenant_id", "public"));
        }
        qw.orderByDesc("updated_at").last("LIMIT 1000");
        List<PartnerConnectionEntity> entities = mapper.selectList(qw);

        List<PartnerConnection> list = new ArrayList<>(entities.size());
        for (PartnerConnectionEntity e : entities) {
            PartnerConnection p = toDomain(e);
            cache.put(p.tenantId() + ":" + p.id(), p);
            cache.put(p.id(), p);
            list.add(p);
        }
        return list;
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
        String currentTenant = com.affiliate.platform.tenant.TenantContext.get();
        if (currentTenant != null) {
            cache.evict(currentTenant + ":" + id);
        }
        cache.evict(id);
    }

    private PartnerConnection toDomain(PartnerConnectionEntity e) {
        Map<String, String> settings;
        try {
            settings = objectMapper.readValue(e.getSettings(), new TypeReference<>() {});
        } catch (Exception ex) {
            settings = Collections.emptyMap();
        }

        String tenant = e.getTenantId() != null ? e.getTenantId() : "public";
        return new PartnerConnection(
                e.getId(),
                tenant,
                e.getName(),
                SupplyType.valueOf(e.getType()),
                e.getEndpoint(),
                settings,
                ConnectionStatus.valueOf(e.getStatus()),
                e.getUpdatedAt()
        );
    }
}
