package com.affiliate.platform.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 工业级两级缓存管理工厂组件 (Two-Tier Cache Manager)
 * <p>
 * 核心架构：
 * 1. L1 堆内缓存：基于 Google Guava Cache，超高性能（< 1 微秒），抵御突发大流量与热点冲击；
 * 2. L2 分布式缓存：基于 Redis（支持可选装配），用于多节点跨实例数据共享与持久化缓存；
 * 3. 容错降级：在未开启 Redis 或 Redis 出现连接异常时，自动平滑降级为纯 Guava 模式，确保业务零中断；
 * 4. 序列化防腐：使用 Jackson 统一对 L2 进行 JSON 序列化存储。
 */
@Component
public class TwoTierCacheManager {

    private static final Logger log = LoggerFactory.getLogger(TwoTierCacheManager.class);

    // 可选注入的 Redis 操作模板
    private final StringRedisTemplate redisTemplate;

    // Jackson JSON 序列化工具
    private final ObjectMapper objectMapper;

    // 已初始化的缓存实例池
    private final ConcurrentMap<String, TwoTierCache<?, ?>> cachePool = new ConcurrentHashMap<>();

    public TwoTierCacheManager(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建或获取两级缓存实例（默认 L1 TTL=60s，L2 TTL=10m）
     *
     * @param cacheName 缓存命名空间（如 "creative", "campaign"）
     * @param valueType 缓存值泛型 Class
     * @param <V>       缓存实体类型
     * @return TwoTierCache 实例
     */
    @SuppressWarnings("unchecked")
    public <V> TwoTierCache<String, V> getOrCreate(String cacheName, Class<V> valueType) {
        return (TwoTierCache<String, V>) cachePool.computeIfAbsent(cacheName,
                name -> new DefaultTwoTierCache<>(name, valueType, Duration.ofSeconds(60), Duration.ofMinutes(10)));
    }

    /**
     * 自定义 TTL 构建两级缓存实例
     */
    @SuppressWarnings("unchecked")
    public <V> TwoTierCache<String, V> getOrCreate(String cacheName, Class<V> valueType, Duration l1Ttl, Duration l2Ttl) {
        return (TwoTierCache<String, V>) cachePool.computeIfAbsent(cacheName,
                name -> new DefaultTwoTierCache<>(name, valueType, l1Ttl, l2Ttl));
    }

    /**
     * 默认两级缓存内部实现类
     */
    private class DefaultTwoTierCache<V> implements TwoTierCache<String, V> {

        private final String namespace;
        private final Class<V> valueType;
        private final Duration defaultL2Ttl;

        // L1: Guava Cache
        private final Cache<String, Object> l1Cache;

        // 空值防穿透标记常量
        private static final String NULL_OBJECT = "__CACHE_NULL_VALUE__";

        public DefaultTwoTierCache(String namespace, Class<V> valueType, Duration l1Ttl, Duration l2Ttl) {
            this.namespace = namespace;
            this.valueType = valueType;
            this.defaultL2Ttl = l2Ttl;
            this.l1Cache = CacheBuilder.newBuilder()
                    .maximumSize(50000)
                    .expireAfterWrite(l1Ttl)
                    .concurrencyLevel(Runtime.getRuntime().availableProcessors())
                    .build();
        }

        private String redisKey(String key) {
            return "cache:" + namespace + ":" + key;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<V> get(String key) {
            // 1. 查询 L1 Guava 堆内缓存
            Object l1Val = l1Cache.getIfPresent(key);
            if (l1Val != null) {
                if (NULL_OBJECT.equals(l1Val)) {
                    return Optional.empty();
                }
                return Optional.of((V) l1Val);
            }

            // 2. 查询 L2 Redis 分布式缓存
            if (redisTemplate != null) {
                try {
                    String json = redisTemplate.opsForValue().get(redisKey(key));
                    if (json != null) {
                        if (NULL_OBJECT.equals(json)) {
                            l1Cache.put(key, NULL_OBJECT);
                            return Optional.empty();
                        }
                        V val = objectMapper.readValue(json, valueType);
                        // 回填 L1
                        l1Cache.put(key, val);
                        return Optional.of(val);
                    }
                } catch (Exception ex) {
                    log.warn("Redis L2 cache read error for key {}: {}", key, ex.getMessage());
                }
            }

            return Optional.empty();
        }

        // 并发互斥锁池，防止高并发热点 Key 缓存击穿 (Cache Stampede Mutex)
        private final ConcurrentMap<String, Object> keyLocks = new ConcurrentHashMap<>();

        private Duration calculateJitteredTtl(Duration baseTtl) {
            if (baseTtl == null) baseTtl = defaultL2Ttl;
            long seconds = baseTtl.getSeconds();
            if (seconds <= 5) return baseTtl;
            // 随机浮动 ±10%，打散大批量缓存失效时间，彻底预防缓存雪崩 (Cache Avalanche)
            long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(-seconds / 10, (seconds / 10) + 1);
            return Duration.ofSeconds(Math.max(1, seconds + jitter));
        }

        @Override
        public V get(String key, Function<String, V> loader) {
            // 1. 快速读取 L1/L2
            Optional<V> cached = get(key);
            if (cached.isPresent()) {
                return cached.get();
            }

            // 2. 双重检查锁定 (Double-Checked Locking)，互斥回源 L3 数据库
            Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
            synchronized (lock) {
                try {
                    cached = get(key);
                    if (cached.isPresent()) {
                        return cached.get();
                    }

                    // 真正执行回源
                    V loaded = loader.apply(key);
                    if (loaded != null) {
                        put(key, loaded);
                    } else {
                        // 缓存空值 30 秒，阻断恶意 Key 连续穿透
                        l1Cache.put(key, NULL_OBJECT);
                        if (redisTemplate != null) {
                            try {
                                redisTemplate.opsForValue().set(redisKey(key), NULL_OBJECT, Duration.ofSeconds(30));
                            } catch (Exception ignored) {}
                        }
                    }
                    return loaded;
                } finally {
                    keyLocks.remove(key, lock);
                }
            }
        }

        @Override
        public void put(String key, V value) {
            put(key, value, defaultL2Ttl);
        }

        @Override
        public void put(String key, V value, Duration ttl) {
            if (key == null) return;
            if (value == null) {
                evict(key);
                return;
            }

            // 1. 写入 L1 Guava
            l1Cache.put(key, value);

            // 2. 写入 L2 Redis（附带随机离散 TTL 防雪崩）
            if (redisTemplate != null) {
                try {
                    String json = objectMapper.writeValueAsString(value);
                    Duration effectiveTtl = calculateJitteredTtl(ttl);
                    redisTemplate.opsForValue().set(redisKey(key), json, effectiveTtl);
                } catch (Exception ex) {
                    log.warn("Redis L2 cache write error for key {}: {}", key, ex.getMessage());
                }
            }
        }

        @Override
        public void evict(String key) {
            if (key == null) return;
            // 1. 淘汰 L1
            l1Cache.invalidate(key);

            // 2. 淘汰 L2
            if (redisTemplate != null) {
                try {
                    redisTemplate.delete(redisKey(key));
                } catch (Exception ex) {
                    log.warn("Redis L2 cache evict error for key {}: {}", key, ex.getMessage());
                }
            }
        }

        @Override
        public void clear() {
            l1Cache.invalidateAll();
            // Redis 支持命名空间匹配删除或依赖独立 key
        }
    }
}
