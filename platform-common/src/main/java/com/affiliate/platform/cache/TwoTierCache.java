package com.affiliate.platform.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * 通用两级缓存顶层契约接口 (Two-Tier Cache Interface)
 * <p>
 * 采用 L1 本地堆内存高速缓存 (Google Guava Cache) + L2 分布式集群缓存 (Redis) + L3 持久化回源机制：
 * 1. 读策略：L1 (Guava, <1µs) -> L2 (Redis, 1~2ms) -> L3 (Database Loader)；
 * 2. 回填策略：命中 L3 或 L2 后，级联回填上一级缓存；
 * 3. 写策略：数据写入或更新后，淘汰 L2 与 L1 缓存（Cache Eviction），保障数据强一致性。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
public interface TwoTierCache<K, V> {

    /**
     * 根据 Key 获取缓存值（依次穿透 L1 和 L2）
     *
     * @param key 缓存键
     * @return 存在则返回包含值的 Optional，双层均未命中则返回 Optional.empty()
     */
    Optional<V> get(K key);

    /**
     * 根据 Key 获取缓存值，若 L1 与 L2 均未命中则调用 loader 加载并级联回填
     *
     * @param key    缓存键
     * @param loader 数据回源加载器（通常为持久化数据库查询）
     * @return 加载到的实体对象（若 loader 返回 null 则返回 null）
     */
    V get(K key, Function<K, V> loader);

    /**
     * 主动写入缓存（同时刷新 L1 和 L2，采用默认过期时间）
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    void put(K key, V value);

    /**
     * 主动写入缓存并指定自定义过期 TTL
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   缓存过期时长
     */
    void put(K key, V value, Duration ttl);

    /**
     * 淘汰指定 Key 的缓存（同时清除 L1 与 L2）
     *
     * @param key 缓存键
     */
    void evict(K key);

    /**
     * 清理当前命名空间下的全量缓存
     */
    void clear();
}
