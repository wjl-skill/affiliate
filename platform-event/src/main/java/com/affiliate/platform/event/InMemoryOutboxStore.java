package com.affiliate.platform.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存并发 Map 的发件箱存储实现 (In-Memory Outbox Store)
 * <p>
 * 在未开启外部 Kafka 与真实数据库时作为默认回退实现，
 * 提供线程安全的发件箱暂存、批量检索与清理模拟。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryOutboxStore implements OutboxStore {

    // 内存待处理事件表：Key 为 eventId，Value 为 DomainEvent 实体
    private final ConcurrentMap<UUID, DomainEvent<?>> pending = new ConcurrentHashMap<>();

    /**
     * 将事件存入待处理 Map
     */
    @Override
    public void append(DomainEvent<?> event) {
        pending.putIfAbsent(event.eventId(), event);
    }

    /**
     * 获取指定上限的待发布事件列表
     */
    @Override
    public List<DomainEvent<?>> pending(int limit) {
        return pending.values().stream().limit(limit).toList();
    }

    /**
     * 事件成功发布后从内存 Map 中移除
     */
    @Override
    public void markPublished(UUID eventId) {
        pending.remove(eventId);
    }

    /**
     * 记录投递失败
     */
    @Override
    public void recordFailure(UUID eventId) {
        // 内存模式下无需持久化失败重试次数
    }
}
