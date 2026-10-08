package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.entity.ClickSessionEntity;
import com.affiliate.platform.mapper.ClickSessionMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点击追踪事件 Kafka 批量消费者 (Click Ingestion Batch Consumer)
 * <p>
 * 订阅 `affiliate.events.click` 主题，批量消费点击事件并批量持久化至 PostgreSQL，
 * 将单条高并发行写转为受控批处理，极大降低数据库事务与 WAL 冲击，实现高并发削峰填谷。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class ClickEventBatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventBatchConsumer.class);

    private final ClickSessionMapper clickSessionMapper;

    @Autowired
    public ClickEventBatchConsumer(ClickSessionMapper clickSessionMapper) {
        this.clickSessionMapper = clickSessionMapper;
    }

    @KafkaListener(topics = "affiliate.events.click", groupId = "affiliate-click-ingestion")
    public void consumeBatch(List<ConsumerRecord<String, ClickSessionEntity>> records) {
        if (records == null || records.isEmpty() || clickSessionMapper == null) {
            return;
        }

        int successCount = 0;
        for (ConsumerRecord<String, ClickSessionEntity> record : records) {
            ClickSessionEntity entity = record.value();
            if (entity == null) continue;

            try {
                clickSessionMapper.insert(entity);
                successCount++;
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                if (msg.contains("duplicate") || msg.contains("unique") || msg.contains("primary") || msg.contains("violates unique")) {
                    log.debug("Click session {} already exists in DB, treated as idempotent success", record.key());
                    successCount++;
                } else {
                    log.error("Failed to persist click record from Kafka (key={}): {}", record.key(), ex.getMessage(), ex);
                    // 抛出非幂等异常，阻止提交 offset，触发 Kafka 监听器重试与 DLQ 死信机制
                    throw new IllegalStateException("Database persistence failure during click ingestion", ex);
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Batch persisted {} / {} click sessions from Kafka", successCount, records.size());
        }
    }
}
