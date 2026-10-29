package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.entity.ClickSessionEntity;
import com.affiliate.platform.mapper.ClickSessionMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ClickEventBatchConsumerTest {

    private ClickSessionMapper clickSessionMapper;
    private ClickEventBatchConsumer consumer;

    @BeforeEach
    void setUp() {
        clickSessionMapper = Mockito.mock(ClickSessionMapper.class);
        consumer = new ClickEventBatchConsumer(clickSessionMapper);
    }

    @Test
    void testConsumeBatchInsertSuccess() {
        ClickSessionEntity entity = new ClickSessionEntity(
                "clk_201", "tenant_a", "off_101", "aff_101", "sub1_test", null, null, null, null,
                "1.1.1.1", "Mozilla/5.0", "US", 1, Instant.now(), Instant.now().plusSeconds(86400)
        );
        when(clickSessionMapper.insertBatchIgnore(anyList())).thenReturn(1);

        ConsumerRecord<String, ClickSessionEntity> record =
                new ConsumerRecord<>("affiliate.events.click", 0, 0L, "clk_201", entity);

        consumer.consumeBatch(List.of(record));

        verify(clickSessionMapper, times(1)).insertBatchIgnore(anyList());
        verify(clickSessionMapper, never()).insert(any(ClickSessionEntity.class));
    }

    @Test
    void testConsumeBatchFallbackWhenBatchInsertFails() {
        ClickSessionEntity entity = new ClickSessionEntity(
                "clk_202", "tenant_a", "off_101", "aff_101", "sub1_test", null, null, null, null,
                "1.1.1.1", "Mozilla/5.0", "US", 1, Instant.now(), Instant.now().plusSeconds(86400)
        );
        when(clickSessionMapper.insertBatchIgnore(anyList())).thenThrow(new RuntimeException("Syntax error or connection failure"));
        when(clickSessionMapper.insert(entity)).thenReturn(1);

        ConsumerRecord<String, ClickSessionEntity> record =
                new ConsumerRecord<>("affiliate.events.click", 0, 1L, "clk_202", entity);

        consumer.consumeBatch(List.of(record));

        verify(clickSessionMapper, times(1)).insertBatchIgnore(anyList());
        verify(clickSessionMapper, times(1)).insert(entity);
    }

    @Test
    void testEmptyBatch() {
        assertDoesNotThrow(() -> consumer.consumeBatch(null));
        assertDoesNotThrow(() -> consumer.consumeBatch(List.of()));
    }
}
