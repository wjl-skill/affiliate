package com.affiliate.platform.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Apache Kafka 的分布式领域事件发布器 (Kafka Event Publisher)
 * <p>
 * 仅在配置 `app.infrastructure.kafka-enabled=true` 时激活。
 * 同时向事务发件箱落盘备份，并向 Kafka 主题异步投递消息。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {

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
        template.send("affiliate." + event.eventType(), event.aggregateId(), event);
    }
}
