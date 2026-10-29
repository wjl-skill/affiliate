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

import java.util.ArrayList;
import java.util.List;

/**
 * 点击追踪事件 Kafka 批量消费者 (Click Ingestion Batch Consumer)
 * <p>
 * 订阅 `affiliate.events.click` 主题，批量消费点击事件并通过单条批量 SQL 持久化至 PostgreSQL，
 * 将单条高并发循环写转为受控批处理，极大降低数据库事务与 WAL 冲击，实现高并发削峰填谷。
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

        List<ClickSessionEntity> batch = new ArrayList<>(records.size());
        for (ConsumerRecord<String, ClickSessionEntity> record : records) {
            if (record != null && record.value() != null) {
                batch.add(record.value());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        try {
            // 1. 优先尝试单条 SQL 批量插入并忽略已存在冲突 (ON CONFLICT DO NOTHING)
            int rows = clickSessionMapper.insertBatchIgnore(batch);
            if (log.isDebugEnabled()) {
                log.debug("Batch persisted {} click sessions in single SQL (batch size: {})", rows, batch.size());
            }
        } catch (Exception ex) {
            log.warn("Batch SQL insert failed, falling back to granular item-by-item persistence: {}", ex.getMessage());
            // 2. 降级为逐条隔离持久化，确保局部脏数据不影响整批消息消费
            int successCount = 0;
            for (ClickSessionEntity entity : batch) {
                try {
                    clickSessionMapper.insert(entity);
                    successCount++;
                } catch (Exception itemEx) {
                    String msg = itemEx.getMessage() != null ? itemEx.getMessage().toLowerCase() : "";
                    if (msg.contains("duplicate") || msg.contains("unique") || msg.contains("primary") || msg.contains("violates unique")) {
                        log.debug("Click session {} already exists in DB, treated as idempotent success", entity.getClickId());
                        successCount++;
                    } else {
                        log.error("Failed to persist click record from Kafka (id={}): {}", entity.getClickId(), itemEx.getMessage(), itemEx);
                        throw new IllegalStateException("Database persistence failure during click ingestion", itemEx);
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("Fallback item-by-item persisted {} / {} click sessions from Kafka", successCount, batch.size());
            }
        }
    }
}
