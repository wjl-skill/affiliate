package com.affiliate.platform.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存并发 Map 的发件箱存储实现 (In-Memory Outbox Store)
 * <p>
 * 在未开启外部 Kafka 与真实数据库时作为默认回退实现，
 * 提供线程安全的发件箱暂存、批量检索、死信沉降与重放功能。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryOutboxStore implements OutboxStore {

    // 内存待处理事件表：Key 为 eventId，Value 为 DomainEvent 实体
    private final ConcurrentMap<UUID, DomainEvent<?>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> attemptsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, EventOutboxDeadEntity> deadMap = new ConcurrentHashMap<>();

    @Override
    public void append(DomainEvent<?> event) {
        pending.putIfAbsent(event.eventId(), event);
    }

    @Override
    public List<DomainEvent<?>> pending(int limit) {
        return pending.values().stream().limit(limit).toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        pending.remove(eventId);
        attemptsMap.remove(eventId);
    }

    @Override
    public void recordFailure(UUID eventId) {
        recordFailure(eventId, "In-memory delivery failure");
    }

    @Override
    public void recordFailure(UUID eventId, String lastError) {
        int count = attemptsMap.merge(eventId, 1, Integer::sum);
        if (count >= 5) {
            moveToDeadLetter(eventId, lastError);
        }
    }

    @Override
    public void moveToDeadLetter(UUID eventId, String lastError) {
        DomainEvent<?> event = pending.remove(eventId);
        int attempts = attemptsMap.getOrDefault(eventId, 5);
        if (event != null) {
            EventOutboxDeadEntity dead = new EventOutboxDeadEntity(
                    event.eventId(),
                    event.tenantId(),
                    event.eventType(),
                    event.aggregateId(),
                    String.valueOf(event.payload()),
                    event.occurredAt(),
                    Instant.now(),
                    attempts,
                    lastError,
                    null
            );
            deadMap.put(eventId, dead);
        }
    }

    @Override
    public List<EventOutboxDeadEntity> getDeadLetters(String tenantId, int limit) {
        return deadMap.values().stream()
                .filter(d -> tenantId == null || tenantId.isBlank() || tenantId.equals(d.getTenantId()))
                .limit(limit)
                .toList();
    }

    @Override
    public boolean replayDeadLetter(UUID eventId) {
        EventOutboxDeadEntity dead = deadMap.get(eventId);
        if (dead == null) {
            return false;
        }
        dead.setReplayedAt(Instant.now());
        attemptsMap.put(eventId, 0);
        // 恢复放回 pending 待投递
        DomainEvent<?> restored = new DomainEvent<>(
                dead.getId(),
                dead.getEventType(),
                1,
                dead.getTenantId(),
                dead.getAggregateId(),
                dead.getOccurredAt(),
                null,
                dead.getPayload()
        );
        pending.put(eventId, restored);
        return true;
    }
}
