package com.affiliate.platform.dsp;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.entity.CampaignEntity;
import com.affiliate.platform.mapper.CampaignMapper;
import com.affiliate.platform.repository.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 MyBatis-Plus 并集成 Guava + Redis 两级缓存的广告活动仓储 (MyBatis-Plus Campaign Repository)
 * <p>
 * 对应数据库表 `campaign`，通过 `CampaignMapper` 实现持久化，结合两级缓存支撑微秒级定向匹配。
 */
@org.springframework.stereotype.Repository("campaignRepository")
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class PostgresCampaignRepository implements Repository<Campaign> {

    private final CampaignMapper mapper;
    private final ObjectMapper objectMapper;
    private final TwoTierCache<String, Campaign> cache;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 1000000);

    public PostgresCampaignRepository(
            CampaignMapper mapper,
            ObjectMapper objectMapper,
            TwoTierCacheManager cacheManager
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.cache = cacheManager.getOrCreate("campaign", Campaign.class);
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + seq.incrementAndGet();
    }

    @Override
    public Campaign save(Campaign c) {
        String domainsJson;
        String devicesJson;
        try {
            domainsJson = objectMapper.writeValueAsString(c.targetDomains() == null ? Set.of() : c.targetDomains());
            devicesJson = objectMapper.writeValueAsString(c.targetDeviceTypes() == null ? Set.of() : c.targetDeviceTypes());
        } catch (Exception e) {
            domainsJson = "[]";
            devicesJson = "[]";
        }

        Instant createdAt = c.createdAt() == null ? Instant.now() : c.createdAt();
        CampaignEntity entity = new CampaignEntity(
                c.id(),
                "public",
                c.advertiserId(),
                c.name(),
                c.startDate(),
                c.endDate(),
                c.dailyBudget(),
                c.maxBid(),
                domainsJson,
                devicesJson,
                c.status().name(),
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
    public Optional<Campaign> find(String id) {
        Campaign val = cache.get(id, key -> {
            CampaignEntity entity = mapper.selectById(key);
            return entity != null ? toDomain(entity) : null;
        });
        return Optional.ofNullable(val);
    }

    @Override
    public List<Campaign> findAll() {
        QueryWrapper<CampaignEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at").last("LIMIT 1000");
        List<CampaignEntity> entities = mapper.selectList(qw);

        List<Campaign> list = new ArrayList<>(entities.size());
        for (CampaignEntity e : entities) {
            Campaign c = toDomain(e);
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

    private Campaign toDomain(CampaignEntity e) {
        Set<String> domains;
        Set<Integer> devices;
        try {
            domains = objectMapper.readValue(e.getTargetDomains(), new TypeReference<>() {});
        } catch (Exception ex) {
            domains = Collections.emptySet();
        }
        try {
            devices = objectMapper.readValue(e.getTargetDeviceTypes(), new TypeReference<>() {});
        } catch (Exception ex) {
            devices = Collections.emptySet();
        }

        return new Campaign(
                e.getId(),
                e.getAdvertiserId(),
                e.getName(),
                e.getStartDate(),
                e.getEndDate(),
                e.getDailyBudget(),
                e.getMaxBid(),
                domains,
                devices,
                Campaign.Status.valueOf(e.getStatus()),
                e.getCreatedAt()
        );
    }
}
