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
            try {
                ClickSessionEntity entity = record.value();
                if (entity != null) {
                    clickSessionMapper.insert(entity);
                    successCount++;
                }
            } catch (Exception ex) {
                log.warn("Failed to persist click record from Kafka (key={}): {}", record.key(), ex.getMessage());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Batch persisted {} / {} click sessions from Kafka", successCount, records.size());
        }
    }
}
