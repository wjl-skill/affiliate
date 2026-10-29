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

import java.util.ArrayList;
import java.util.List;

/**
 * S2S 转化归因事件 Kafka 批量消费者 (Conversion Ingestion Batch Consumer)
 * <p>
 * 订阅 `affiliate.events.conversion` 主题，批量消费转化事实并通过原生 Upsert 批处理持久化至 PostgreSQL，
 * 将高并发归因写入与主事务解耦，极大消除 N+1 查询开销并保障峰值吞吐量。
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

        List<ConversionEntity> batch = new ArrayList<>(records.size());
        for (ConsumerRecord<String, ConversionEntity> record : records) {
            if (record != null && record.value() != null) {
                batch.add(record.value());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        try {
            // 1. 优先尝试单条 SQL 批量 Upsert
            conversionMapper.upsertBatch(batch);
        } catch (Exception ex) {
            log.warn("Batch SQL upsert failed, falling back to item-by-item persistence: {}", ex.getMessage());
            // 2. 降级为逐条隔离持久化
            for (ConversionEntity entity : batch) {
                try {
                    if (conversionMapper.selectById(entity.getId()) != null) {
                        conversionMapper.updateById(entity);
                    } else {
                        conversionMapper.insert(entity);
                    }
                } catch (Exception itemEx) {
                    String msg = itemEx.getMessage() != null ? itemEx.getMessage().toLowerCase() : "";
                    if (msg.contains("duplicate") || msg.contains("unique") || msg.contains("primary") || msg.contains("violates unique")) {
                        log.debug("Conversion {} already processed in DB, treated as idempotent success", entity.getId());
                    } else {
                        log.error("Failed to persist conversion record from Kafka (id={}): {}", entity.getId(), itemEx.getMessage(), itemEx);
                        throw new IllegalStateException("Database persistence failure during conversion ingestion", itemEx);
                    }
                }
            }
        }

        // 3. 驱动下游多维分析度量统计
        if (analyticsService != null) {
            for (ConversionEntity entity : batch) {
                if ("APPROVED".equalsIgnoreCase(entity.getStatus())) {
                    try {
                        analyticsService.recordConversion(
                                entity.getAffiliateId(),
                                entity.getSub1(),
                                entity.getPayout(),
                                entity.getRevenue()
                        );
                    } catch (Exception analyticsEx) {
                        log.warn("Failed to record SubID conversion metrics for {}: {}", entity.getId(), analyticsEx.getMessage());
                    }
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Batch processed {} conversions from Kafka", batch.size());
        }
    }
}
