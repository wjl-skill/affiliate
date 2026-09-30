package com.affiliate.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 事务发件箱后台中继投递调度器 (Transactional Outbox Relay Scheduler)
 * <p>
 * 定时（默认每秒）轮询发件箱中未完成投递的事件，可靠投递至 Kafka，
 * 支持 Redis 分布式防重锁，避免多 Pod 集群并发重复投递，
 * 投递成功后更新 published_at，失败则累加重试计数，彻底闭环分布式事务最终一致性。
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);
    private static final String RELAY_LOCK_KEY = "lock:outbox:relay";

    // 事务发件箱仓储
    private final OutboxStore outbox;

    // Kafka 发送客户端
    private final KafkaTemplate<String, Object> kafka;

    // Redis 分布式锁客户端
    private final StringRedisTemplate redisTemplate;

    public OutboxRelayScheduler(OutboxStore outbox, KafkaTemplate<String, Object> kafka) {
        this(outbox, kafka, null);
    }

    @Autowired
    public OutboxRelayScheduler(
            OutboxStore outbox,
            KafkaTemplate<String, Object> kafka,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 定时中继投递任务循环 (带分布式防重锁)
     */
    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void relay() {
        boolean lockAcquired = false;
        if (redisTemplate != null) {
            try {
                Boolean locked = redisTemplate.opsForValue().setIfAbsent(RELAY_LOCK_KEY, "1", Duration.ofSeconds(3));
                lockAcquired = Boolean.TRUE.equals(locked);
                if (!lockAcquired) {
                    // 其他 Pod 实例正在执行中继投递，跳过避免重复拉取
                    return;
                }
            } catch (Exception ex) {
                // Redis 异常时单机放行
                log.warn("Failed to acquire outbox relay lock, proceeding anyway: {}", ex.getMessage());
            }
        }

        try {
            // 批量拉取最多 100 条待处理事件 (FOR UPDATE SKIP LOCKED)
            List<DomainEvent<?>> pending = outbox.pending(100);
            for (DomainEvent<?> event : pending) {
                String topic = "affiliate." + event.eventType();
                try {
                    // 同步等待至多 3 秒，确保投递成功后再标记发布
                    kafka.send(topic, event.aggregateId(), event).get(3, TimeUnit.SECONDS);
                    outbox.markPublished(event.eventId());
                } catch (Exception ex) {
                    log.error("Failed to relay outbox event {} to topic {}", event.eventId(), topic, ex);
                    outbox.recordFailure(event.eventId());
                }
            }
        } finally {
            if (lockAcquired && redisTemplate != null) {
                try {
                    redisTemplate.delete(RELAY_LOCK_KEY);
                } catch (Exception ignored) {}
            }
        }
    }
}
