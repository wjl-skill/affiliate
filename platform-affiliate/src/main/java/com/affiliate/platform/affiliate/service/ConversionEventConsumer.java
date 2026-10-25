package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.entity.ConversionEntity;
import com.affiliate.platform.mapper.ConversionMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S2S 转化归因事件 Kafka 批量消费者 (Conversion Ingestion Batch Consumer)
 * <p>
 * 订阅 `affiliate.events.conversion` 主题，异步持久化并驱动多维归因计数，
 * 将高并发归因写入与主事务解耦，保障峰值吞吐量。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class ConversionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConversionEventConsumer.class);

    private final ConversionMapper conversionMapper;
    private final SubIdAnalyticsService analyticsService;

    @Autowired
    public ConversionEventConsumer(
            ConversionMapper conversionMapper,
            @Autowired(required = false) SubIdAnalyticsService analyticsService
    ) {
        this.conversionMapper = conversionMapper;
        this.analyticsService = analyticsService;
    }

    @KafkaListener(topics = "affiliate.events.conversion", groupId = "affiliate-conversion-ingestion")
    public void consumeBatch(List<ConsumerRecord<String, ConversionEntity>> records) {
        if (records == null || records.isEmpty() || conversionMapper == null) {
            return;
        }

        int successCount = 0;
        for (ConsumerRecord<String, ConversionEntity> record : records) {
            ConversionEntity entity = record.value();
            if (entity == null) continue;

            try {
                if (conversionMapper.selectById(entity.getId()) != null) {
                    conversionMapper.updateById(entity);
                } else {
                    conversionMapper.insert(entity);
                }

                if (analyticsService != null && "APPROVED".equalsIgnoreCase(entity.getStatus())) {
                    analyticsService.recordConversion(
                            entity.getAffiliateId(),
                            entity.getSub1(),
                            entity.getPayout(),
                            entity.getRevenue()
                    );
                }
                successCount++;
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                if (msg.contains("duplicate") || msg.contains("unique") || msg.contains("primary") || msg.contains("violates unique")) {
                    log.debug("Conversion {} already processed in DB, treated as idempotent success", record.key());
                    successCount++;
                } else {
                    log.error("Failed to persist conversion record from Kafka (key={}): {}", record.key(), ex.getMessage(), ex);
                    throw new IllegalStateException("Database persistence failure during conversion ingestion", ex);
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Batch processed {} / {} conversions from Kafka", successCount, records.size());
        }
    }
}
