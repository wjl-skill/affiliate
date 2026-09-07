package com.affiliate.platform.repository;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.domain.Auction;
import com.affiliate.platform.entity.AuctionEntity;
import com.affiliate.platform.mapper.AuctionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 MyBatis-Plus 并集成 Guava + Redis 两级缓存的拍卖成交仓储 (MyBatis-Plus Auction Repository)
 * <p>
 * 采用 MyBatis-Plus `AuctionMapper` 快速持久化实时撮合成交事实。
 */
@Repository
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class PostgresAuctionRepository implements com.affiliate.platform.repository.Repository<Auction> {

    private final AuctionMapper mapper;
    private final TwoTierCache<String, Auction> cache;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis() % 1000000);

    public PostgresAuctionRepository(AuctionMapper mapper, TwoTierCacheManager cacheManager) {
        this.mapper = mapper;
        this.cache = cacheManager.getOrCreate("auction", Auction.class);
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + seq.incrementAndGet();
    }

    @Override
    public Auction save(Auction a) {
        Instant createdAt = a.createdAt() == null ? Instant.now() : a.createdAt();
        AuctionEntity entity = new AuctionEntity(
                a.id(),
                "public",
                a.requestId(),
                a.adSlotId(),
                a.creativeId(),
                a.clearingPrice(),
                a.currency(),
                a.advertiser(),
                createdAt
        );

        if (mapper.selectById(a.id()) != null) {
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }

        cache.put(a.id(), a);
        return a;
    }

    @Override
    public Optional<Auction> find(String id) {
        Auction val = cache.get(id, key -> {
            AuctionEntity e = mapper.selectById(key);
            return e != null ? toDomain(e) : null;
        });
        return Optional.ofNullable(val);
    }

    @Override
    public List<Auction> findAll() {
        QueryWrapper<AuctionEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at").last("LIMIT 1000");
        List<AuctionEntity> entities = mapper.selectList(qw);

        List<Auction> list = new ArrayList<>(entities.size());
        for (AuctionEntity e : entities) {
            Auction a = toDomain(e);
            cache.put(a.id(), a);
            list.add(a);
        }
        return list;
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
        cache.evict(id);
    }

    private Auction toDomain(AuctionEntity e) {
        return new Auction(
                e.getId(),
                e.getRequestId(),
                e.getAdSlotId(),
                e.getCreativeId(),
                e.getClearingPrice(),
                e.getCurrency(),
                e.getAdvertiser(),
                e.getCreatedAt()
        );
    }
}
