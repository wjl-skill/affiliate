package com.affiliate.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事务发件箱后台中继投递调度器 (Transactional Outbox Relay Scheduler)
 * <p>
 * 定时轮询发件箱中未完成投递的事件，可靠中继投递至 Kafka。
 * 生产加固：
 * 1. 采用带 Owner 唯一凭证的分布式租约锁，并通过 Lua 脚本安全释放，防止长任务超时释放后误删其他节点新锁；
 * 2. 具备单机并发防重入（AtomicBoolean）；
 * 3. 投递成功后更新 markPublished，投递失败累加重试计数。
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);
    private static final String RELAY_LOCK_KEY = "lock:outbox:relay";
    private static final Duration LOCK_LEASE_DURATION = Duration.ofSeconds(10);

    // 安全释放分布式锁 Lua 脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end",
            Long.class
    );

    // 事务发件箱仓储
    private final OutboxStore outbox;

    // Kafka 发送客户端
    private final KafkaTemplate<String, Object> kafka;

    // Redis 分布式锁客户端
    private final StringRedisTemplate redisTemplate;

    // 本机防重入标志
    private final AtomicBoolean isRelaying = new AtomicBoolean(false);

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
     * 定时中继投递任务循环 (带租约防误删分布式锁与单机并发保护)
     */
    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void relay() {
        if (!isRelaying.compareAndSet(false, true)) {
            // 本机上一轮中继尚未结束，跳过避免线程拥堵
            return;
        }

        String lockOwner = UUID.randomUUID().toString();
        boolean lockAcquired = false;

        try {
            if (redisTemplate != null) {
                try {
                    Boolean locked = redisTemplate.opsForValue().setIfAbsent(RELAY_LOCK_KEY, lockOwner, LOCK_LEASE_DURATION);
                    lockAcquired = Boolean.TRUE.equals(locked);
                    if (!lockAcquired) {
                        // 其他 Pod 实例正在执行中继投递，跳过
                        return;
                    }
                } catch (Exception ex) {
                    log.warn("Failed to acquire outbox relay lock, proceeding locally: {}", ex.getMessage());
                }
            }

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
                    redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(RELAY_LOCK_KEY), lockOwner);
                } catch (Exception ex) {
                    log.warn("Failed to safely release outbox relay lock: {}", ex.getMessage());
                }
            }
            isRelaying.set(false);
        }
    }
}
