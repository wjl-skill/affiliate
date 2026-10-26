package com.affiliate.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 基于 MyBatis-Plus 的企业级事务发件箱存储实现 (JDBC Outbox Store)
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class JdbcOutboxStore implements OutboxStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcOutboxStore.class);

    private final EventOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public JdbcOutboxStore(EventOutboxMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(DomainEvent<?> event) {
        try {
            EventOutboxEntity entity = new EventOutboxEntity();
            entity.setId(event.eventId());
            entity.setTenantId(event.tenantId());
            entity.setEventType(event.eventType());
            entity.setAggregateId(event.aggregateId());
            entity.setPayload(objectMapper.writeValueAsString(event.payload()));
            entity.setOccurredAt(event.occurredAt());
            mapper.append(entity);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot append outbox event", ex);
        }
    }

    @Override
    public List<DomainEvent<?>> pending(int limit) {
        try {
            return mapper.pending(Math.max(1, Math.min(limit, 1000))).stream().map(this::toEvent).toList();
        } catch (Exception ex) {
            log.error("Failed to fetch pending outbox events", ex);
            return List.of();
        }
    }

    @Override
    public void markPublished(UUID eventId) {
        mapper.markPublished(eventId);
    }

    @Override
    public void recordFailure(UUID eventId) {
        recordFailure(eventId, "Unknown Kafka send failure");
    }

    @Override
    public void recordFailure(UUID eventId, String lastError) {
        EventOutboxEntity entity = mapper.selectById(eventId);
        if (entity != null && entity.getAttempts() != null && entity.getAttempts() >= 4) {
            log.error("[CRITICAL DLT] 发件箱事件重试次数达上限 (attempts >= 5)，移入死信队列: eventId={}, tenant={}, cause={}",
                    eventId, entity.getTenantId(), lastError);
            moveToDeadLetter(eventId, lastError);
        } else {
            mapper.recordFailureWithCause(eventId, lastError);
        }
    }

    @Override
    public void moveToDeadLetter(UUID eventId, String lastError) {
        mapper.moveToDeadLetter(eventId, lastError);
        mapper.deleteFromOutbox(eventId);
    }

    @Override
    public List<EventOutboxDeadEntity> getDeadLetters(String tenantId, int limit) {
        return mapper.selectDeadLetters(tenantId, Math.max(1, Math.min(limit, 500)));
    }

    @Override
    public boolean replayDeadLetter(UUID eventId) {
        EventOutboxDeadEntity dead = mapper.selectDeadLetterById(eventId);
        if (dead == null) {
            return false;
        }
        mapper.markDeadLetterReplayed(eventId);
        mapper.reinsertToOutbox(eventId);
        log.info("[DLT Replay] 死信事件已重放回发件箱 pending 列表: eventId={}, tenant={}", eventId, dead.getTenantId());
        return true;
    }

    private DomainEvent<?> toEvent(EventOutboxEntity entity) {
        Object payload;
        try {
            payload = objectMapper.readValue(entity.getPayload(), Object.class);
        } catch (Exception ex) {
            payload = entity.getPayload();
        }
        return new DomainEvent<>(
                entity.getId(),
                entity.getEventType(),
                1,
                entity.getTenantId(),
                entity.getAggregateId(),
                entity.getOccurredAt(),
                null,
                payload
        );
    }
}
