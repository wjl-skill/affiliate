package com.affiliate.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 基于 Apache Kafka 的分布式领域事件发布器 (Kafka Event Publisher)
 * <p>
 * 仅在配置 `app.infrastructure.kafka-enabled=true` 时激活。
 * 生产加固：采用事务发件箱 (Outbox)、本地实时流订阅总线与 Kafka 异步确认联动机制。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> template;
    private final OutboxStore outbox;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final List<Consumer<DomainEvent<?>>> subscribers = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    private KafkaEventStreamBridge streamBridge;

    public KafkaEventPublisher(KafkaTemplate<String, Object> template, OutboxStore outbox) {
        this(template, outbox, null);
    }

    @Autowired
    public KafkaEventPublisher(
            KafkaTemplate<String, Object> template,
            OutboxStore outbox,
            @Autowired(required = false) ApplicationEventPublisher applicationEventPublisher
    ) {
        this.template = template;
        this.outbox = outbox;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        if (event == null) {
            return;
        }
        // 1. 本地发件箱落盘保障最终一致性
        outbox.append(event);

        // 2. 触发本地实时订阅者与 Spring 事件监听，并登记去重缓存防止同进程 Kafka 回环消费
        if (streamBridge != null && event.eventId() != null) {
            streamBridge.markLocallyProcessed(event.eventId());
        }
        dispatchLocal(event);

        // 3. 根据事件类型动态路由至对应 Kafka Topic，以 aggregateId 作为分区键保证顺序性
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

    @Override
    public void dispatchLocal(DomainEvent<?> event) {
        if (event == null) {
            return;
        }
        for (Consumer<DomainEvent<?>> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception ex) {
                log.warn("[KafkaEventBus] Local subscriber error handling event {}: {}", event.eventId(), ex.getMessage());
            }
        }
        if (applicationEventPublisher != null) {
            try {
                applicationEventPublisher.publishEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void subscribe(Consumer<DomainEvent<?>> subscriber) {
        if (subscriber != null) {
            subscribers.add(subscriber);
        }
    }
}
