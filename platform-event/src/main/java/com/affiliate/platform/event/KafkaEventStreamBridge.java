package com.affiliate.platform.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 跨进程 Kafka 领域事件流消费桥接器 (Cross-JVM Kafka Domain Event Stream Bridge)
 * <p>
 * 仅在 {@code app.infrastructure.kafka-enabled=true} 时激活。
 * 负责将来自其他微服务平面（如 RTB / Tracker）的 Kafka 领域事件反序列化并分发至本进程的
 * {@link EventPublisher#dispatchLocal(DomainEvent)} 订阅者（计费、OLAP、CDP、DSP 在线学习），
 * 同时内置有界 LRU 去重缓存防止同进程双发或 Kafka 重投导致重复处理。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class KafkaEventStreamBridge {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventStreamBridge.class);
    private static final int MAX_DEDUP_CACHE_SIZE = 10_000;

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Set<UUID> recentEventIds;

    private final AtomicLong consumedCount = new AtomicLong(0);
    private final AtomicLong deduplicatedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    @Autowired
    public KafkaEventStreamBridge(
            EventPublisher eventPublisher,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.recentEventIds = Collections.synchronizedSet(Collections.newSetFromMap(
                new LinkedHashMap<>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                        return size() > MAX_DEDUP_CACHE_SIZE;
                    }
                }
        ));
    }

    @KafkaListener(
            topics = {
                    "affiliate.auction.win.v1",
                    "affiliate.impression.recorded.v1",
                    "affiliate.click.recorded.v1",
                    "affiliate.conversion.approved.v1"
            },
            groupId = "${spring.application.name:affiliate-platform}-event-bridge"
    )
    public void onKafkaMessage(Object message) {
        if (message == null || eventPublisher == null) {
            return;
        }
        try {
            DomainEvent<?> event = normalize(message);
            if (event == null) {
                return;
            }
            if (event.eventId() != null && !recentEventIds.add(event.eventId())) {
                deduplicatedCount.incrementAndGet();
                return;
            }
            eventPublisher.dispatchLocal(event);
            consumedCount.incrementAndGet();
        } catch (Exception ex) {
            failedCount.incrementAndGet();
            log.warn("[KafkaEventBridge] Failed to process incoming Kafka domain event: {}", ex.getMessage());
        }
    }

    /**
     * 标记某事件 ID 已在当前进程本地处理（供同进程发布侧调用以防止回环重复消费）
     */
    public void markLocallyProcessed(UUID eventId) {
        if (eventId != null) {
            recentEventIds.add(eventId);
        }
    }

    private DomainEvent<?> normalize(Object message) throws Exception {
        if (message instanceof DomainEvent<?> domainEvent) {
            return domainEvent;
        }
        if (message instanceof String json) {
            if (json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<DomainEvent<Map<String, Object>>>() {});
        }
        if (message instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<DomainEvent<Map<String, Object>>>() {});
        }
        return objectMapper.convertValue(message, new TypeReference<DomainEvent<Map<String, Object>>>() {});
    }

    public long getConsumedCount() {
        return consumedCount.get();
    }

    public long getDeduplicatedCount() {
        return deduplicatedCount.get();
    }

    public long getFailedCount() {
        return failedCount.get();
    }
}
