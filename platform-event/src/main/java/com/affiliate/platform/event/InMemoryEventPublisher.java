package com.affiliate.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 基于内存并发队列与实时订阅总线的轻量级事件发布器 (In-Memory Event Publisher & Real-Time Bus)
 * <p>
 * 在未启用外部 Kafka 消息集群时作为默认回退组件，
 * 同时向内存发件箱、内部队列、注册订阅者及 Spring {@link ApplicationEventPublisher} 实时广播领域事件。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);

    // 内存事件缓冲队列
    private final ConcurrentLinkedQueue<DomainEvent<?>> events = new ConcurrentLinkedQueue<>();

    // 实时流式消费者注册表
    private final List<Consumer<DomainEvent<?>>> subscribers = new CopyOnWriteArrayList<>();

    // 发件箱存储端口
    private final OutboxStore outbox;

    // Spring 应用事件广播器（可选）
    private final ApplicationEventPublisher applicationEventPublisher;

    public InMemoryEventPublisher(OutboxStore outbox) {
        this(outbox, null);
    }

    @Autowired
    public InMemoryEventPublisher(
            OutboxStore outbox,
            @Autowired(required = false) ApplicationEventPublisher applicationEventPublisher
    ) {
        this.outbox = outbox;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        if (event == null) {
            return;
        }
        outbox.append(event);
        events.add(event);

        for (Consumer<DomainEvent<?>> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception ex) {
                log.warn("[EventBus] Subscriber error handling event {} ({}): {}",
                        event.eventId(), event.eventType(), ex.getMessage());
            }
        }

        if (applicationEventPublisher != null) {
            try {
                applicationEventPublisher.publishEvent(event);
            } catch (Exception ex) {
                log.debug("[EventBus] Spring ApplicationEventPublisher dispatch warning for {}: {}",
                        event.eventType(), ex.getMessage());
            }
        }
    }

    @Override
    public void subscribe(Consumer<DomainEvent<?>> subscriber) {
        if (subscriber != null) {
            subscribers.add(subscriber);
        }
    }

    /**
     * 获取当前内存队列中暂存的事件总数（单测断言使用）
     */
    public int size() {
        return events.size();
    }
}
