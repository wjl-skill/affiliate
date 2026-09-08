package com.affiliate.platform.affiliate.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存管理器
 * <p>
 * 三级缓存架构：
 * L1: Caffeine 本地缓存（进程内，毫秒级）
 * L2: Redis 分布式缓存（跨实例，<10ms）
 * L3: PostgreSQL 数据库（持久化存储）
 * <p>
 * 缓存策略：
 * - 读取：L1 -> L2 -> L3（数据库），逐级回填
 * - 写入：直接写入 L3，同时失效 L1 和 L2
 * - 删除：同时删除 L1、L2、L3
 */
@Component
public class MultiLevelCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;

    // L1 缓存容器（按业务域分隔）
    private final Cache<String, Object> l1Cache;

    // 缓存配置
    private static final int L1_MAX_SIZE = 10000;
    private static final Duration L1_EXPIRE_AFTER_WRITE = Duration.ofMinutes(5);
    private static final Duration L1_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(10);
    private static final Duration L2_DEFAULT_TTL = Duration.ofMinutes(30);

    public MultiLevelCacheManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;

        // 初始化 L1 缓存
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(L1_MAX_SIZE)
                .expireAfterWrite(L1_EXPIRE_AFTER_WRITE)
                .expireAfterAccess(L1_EXPIRE_AFTER_ACCESS)
                .recordStats()
                .build();
    }

    /**
     * 获取缓存数据（自动穿透三级缓存）
     *
     * @param key 缓存键
     * @param type 返回类型
     * @param dbLoader 数据库加载器（L3）
     * @param <T> 泛型类型
     * @return Optional 包装的结果
     */
    public <T> Optional<T> get(String key, Class<T> type, Supplier<T> dbLoader) {
        // L1: 本地缓存查询
        Object l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            return Optional.of(type.cast(l1Value));
        }

        // L2: Redis 缓存查询
        Object l2Value = redisTemplate.opsForValue().get(key);
        if (l2Value != null) {
            // 回填 L1
            l1Cache.put(key, l2Value);
            return Optional.of(type.cast(l2Value));
        }

        // L3: 数据库查询
        T dbValue = dbLoader.get();
        if (dbValue != null) {
            // 回填 L2 和 L1
            redisTemplate.opsForValue().set(key, dbValue, L2_DEFAULT_TTL);
            l1Cache.put(key, dbValue);
            return Optional.of(dbValue);
        }

        return Optional.empty();
    }

    /**
     * 获取缓存数据（带自定义 TTL）
     */
    public <T> Optional<T> get(String key, Class<T> type, Duration l2Ttl, Supplier<T> dbLoader) {
        // L1 查询
        Object l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            return Optional.of(type.cast(l1Value));
        }

        // L2 查询
        Object l2Value = redisTemplate.opsForValue().get(key);
        if (l2Value != null) {
            l1Cache.put(key, l2Value);
            return Optional.of(type.cast(l2Value));
        }

        // L3 查询
        T dbValue = dbLoader.get();
        if (dbValue != null) {
            redisTemplate.opsForValue().set(key, dbValue, l2Ttl);
            l1Cache.put(key, dbValue);
            return Optional.of(dbValue);
        }

        return Optional.empty();
    }

    /**
     * 写入缓存（同时写入所有层级）
     */
    public <T> void put(String key, T value) {
        put(key, value, L2_DEFAULT_TTL);
    }

    /**
     * 写入缓存（带自定义 TTL）
     */
    public <T> void put(String key, T value, Duration l2Ttl) {
        // 写入 L1
        l1Cache.put(key, value);

        // 写入 L2
        if (l2Ttl != null) {
            redisTemplate.opsForValue().set(key, value, l2Ttl);
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
    }

    /**
     * 失效缓存（清除 L1 和 L2）
     */
    public void evict(String key) {
        // 清除 L1
        l1Cache.invalidate(key);

        // 清除 L2
        redisTemplate.delete(key);
    }

    /**
     * 批量失效缓存
     */
    public void evictAll(String... keys) {
        for (String key : keys) {
            evict(key);
        }
    }

    /**
     * 按前缀失效缓存
     */
    public void evictByPattern(String pattern) {
        // L1: 清除所有（简化实现）
        l1Cache.invalidateAll();

        // L2: 按模式删除
        var redisKeys = redisTemplate.keys(pattern);
        if (redisKeys != null && !redisKeys.isEmpty()) {
            redisTemplate.delete(redisKeys);
        }
    }

    /**
     * 仅从 L1 获取（快速路径，不触发数据库）
     */
    public <T> Optional<T> getFromL1Only(String key, Class<T> type) {
        Object value = l1Cache.getIfPresent(key);
        return value != null ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /**
     * 仅从 L2 获取
     */
    public <T> Optional<T> getFromL2Only(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /**
     * 预热缓存（批量加载到 L1 和 L2）
     */
    public <T> void warmUp(String key, T value, Duration l2Ttl) {
        put(key, value, l2Ttl);
    }

    /**
     * 获取 L1 缓存统计
     */
    public CacheStats getL1Stats() {
        var stats = l1Cache.stats();
        return new CacheStats(
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate(),
                stats.evictionCount(),
                l1Cache.estimatedSize()
        );
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        l1Cache.invalidateAll();
        // 注意：清空 Redis 需谨慎，这里只清除特定前缀
    }

    /**
     * 缓存统计记录
     */
    public record CacheStats(
            long hitCount,
            long missCount,
            double hitRate,
            long evictionCount,
            long size
    ) {}
}
