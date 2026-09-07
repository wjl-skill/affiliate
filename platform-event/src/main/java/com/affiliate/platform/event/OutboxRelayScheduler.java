package com.affiliate.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 事务发件箱后台中继投递调度器 (Transactional Outbox Relay Scheduler)
 * <p>
 * 定时（默认每秒）轮询发件箱中未完成投递的事件，可靠投递至 Kafka，
 * 投递成功后更新 published_at，失败则累加重试计数，彻底闭环分布式事务最终一致性。
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    // 事务发件箱仓储
    private final OutboxStore outbox;

    // Kafka 发送客户端
    private final KafkaTemplate<String, Object> kafka;

    public OutboxRelayScheduler(OutboxStore outbox, KafkaTemplate<String, Object> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    /**
     * 定时中继投递任务循环
     */
    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void relay() {
        // 批量拉取最多 100 条待处理事件
        List<DomainEvent<?>> pending = outbox.pending(100);
        for (DomainEvent<?> event : pending) {
            String topic = "affiliate." + event.eventType();
            try {
                // 异步发送至 Kafka
                kafka.send(topic, event.aggregateId(), event).whenComplete((result, ex) -> {
                    if (ex == null) {
                        // 发送成功：标记该事件已发布
                        outbox.markPublished(event.eventId());
                    } else {
                        // 发送失败：记录错误日志并累加失败次数
                        log.error("Failed to relay outbox event {} to topic {}", event.eventId(), topic, ex);
                        outbox.recordFailure(event.eventId());
                    }
                });
            } catch (Exception ex) {
                log.error("Error dispatching outbox event {} to Kafka", event.eventId(), ex);
                outbox.recordFailure(event.eventId());
            }
        }
    }
}
