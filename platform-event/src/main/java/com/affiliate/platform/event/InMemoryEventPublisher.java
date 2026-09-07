package com.affiliate.platform.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 基于内存并发队列的轻量级事件发布器 (In-Memory Event Publisher)
 * <p>
 * 在未启用外部 Kafka 消息集群时作为默认回退组件，
 * 同时向内存发件箱与内部 ConcurrentLinkedQueue 中暂存事件，便于本地调试与单元测试。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryEventPublisher implements EventPublisher {

    // 内存事件缓冲队列
    private final ConcurrentLinkedQueue<DomainEvent<?>> events = new ConcurrentLinkedQueue<>();

    // 发件箱存储端口
    private final OutboxStore outbox;

    public InMemoryEventPublisher(OutboxStore outbox) {
        this.outbox = outbox;
    }

    /**
     * 将领域事件追加至发件箱与内存广播队列
     *
     * @param event 待发布事件
     */
    @Override
    public void publish(DomainEvent<?> event) {
        outbox.append(event);
        events.add(event);
    }

    /**
     * 获取当前内存队列中暂存的事件总数（单测断言使用）
     */
    public int size() {
        return events.size();
    }
}
