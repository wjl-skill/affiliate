package com.affiliate.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 基于 MyBatis-Plus 的事务发件箱存储。 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class JdbcOutboxStore implements OutboxStore {
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
            entity.setId(event.eventId()); entity.setTenantId(event.tenantId()); entity.setEventType(event.eventType());
            entity.setAggregateId(event.aggregateId()); entity.setPayload(objectMapper.writeValueAsString(event.payload())); entity.setOccurredAt(event.occurredAt());
            mapper.append(entity);
        } catch (Exception ex) { throw new IllegalStateException("cannot append outbox event", ex); }
    }

    @Override
    public List<DomainEvent<?>> pending(int limit) {
        try {
            return mapper.pending(Math.max(1, Math.min(limit, 1000))).stream().map(this::toEvent).toList();
        } catch (Exception ex) { return List.of(); }
    }

    @Override public void markPublished(UUID eventId) { mapper.markPublished(eventId); }
    @Override public void recordFailure(UUID eventId) { mapper.recordFailure(eventId); }

    private DomainEvent<?> toEvent(EventOutboxEntity entity) {
        Object payload;
        try { payload = objectMapper.readValue(entity.getPayload(), Object.class); } catch (Exception ex) { payload = entity.getPayload(); }
        return new DomainEvent<>(entity.getId(), entity.getEventType(), 1, entity.getTenantId(), entity.getAggregateId(), entity.getOccurredAt(), null, payload);
    }
}
