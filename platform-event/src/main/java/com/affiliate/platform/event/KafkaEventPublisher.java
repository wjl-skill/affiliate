package com.affiliate.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Apache Kafka 的分布式领域事件发布器 (Kafka Event Publisher)
 * <p>
 * 仅在配置 `app.infrastructure.kafka-enabled=true` 时激活。
 * 生产加固：采用事务发件箱 (Outbox) 与异步确认联动机制。
 * 直发成功后立即标记 markPublished，彻底消除中继调度器重复投递导致的“全量双发”；
 * 直发网络抖动时保留 pending 状态，由 OutboxRelayScheduler 自动补偿重试。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    // Spring Kafka 发送客户端模板
    private final KafkaTemplate<String, Object> template;

    // 事务发件箱存储
    private final OutboxStore outbox;

    public KafkaEventPublisher(KafkaTemplate<String, Object> template, OutboxStore outbox) {
        this.template = template;
        this.outbox = outbox;
    }

    /**
     * 将领域事件追加至本地发件箱并推送到对应的 Kafka Topic（如 "affiliate.creative.created.v1"）
     *
     * @param event 待发布的领域事件实体
     */
    @Override
    public void publish(DomainEvent<?> event) {
        // 1. 本地发件箱落盘保障最终一致性
        outbox.append(event);
        // 2. 根据事件类型动态路由至对应 Kafka Topic，以 aggregateId 作为分区键保证顺序性
        String topic = "affiliate." + event.eventType();
        try {
            template.send(topic, event.aggregateId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            outbox.markPublished(event.eventId());
                        } else {
                            log.warn("Direct Kafka send failed for event {} to topic {}, will be relayed by OutboxRelayScheduler: {}",
                                    event.eventId(), topic, ex.getMessage());
                            outbox.recordFailure(event.eventId());
                        }
                    });
        } catch (Exception ex) {
            log.warn("Synchronous submission to Kafka failed for event {} to topic {}, deferring to OutboxRelayScheduler: {}",
                    event.eventId(), topic, ex.getMessage());
            outbox.recordFailure(event.eventId());
        }
    }
}
