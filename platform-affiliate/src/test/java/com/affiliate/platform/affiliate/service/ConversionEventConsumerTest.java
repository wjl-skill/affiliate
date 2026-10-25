package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.entity.ConversionEntity;
import com.affiliate.platform.mapper.ConversionMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversionEventConsumerTest {

    private ConversionMapper conversionMapper;
    private SubIdAnalyticsService analyticsService;
    private ConversionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        conversionMapper = Mockito.mock(ConversionMapper.class);
        analyticsService = Mockito.mock(SubIdAnalyticsService.class);
        consumer = new ConversionEventConsumer(conversionMapper, analyticsService);
    }

    @Test
    void testConsumeBatchInsertNewConversion() {
        ConversionEntity entity = new ConversionEntity(
                "conv_101", "tenant_a", "clk_101", "off_101", "aff_101",
                "tx_101", BigDecimal.valueOf(15.00), BigDecimal.valueOf(25.00),
                BigDecimal.valueOf(100.00), "APPROVED", 12L, null, "sub1_tiktok",
                "PENDING", Instant.now()
        );
        when(conversionMapper.selectById("conv_101")).thenReturn(null);

        ConsumerRecord<String, ConversionEntity> record =
                new ConsumerRecord<>("affiliate.events.conversion", 0, 0L, "conv_101", entity);

        consumer.consumeBatch(List.of(record));

        verify(conversionMapper, times(1)).insert(entity);
        verify(analyticsService, times(1)).recordConversion(
                eq("aff_101"), eq("sub1_tiktok"),
                eq(BigDecimal.valueOf(15.00)), eq(BigDecimal.valueOf(25.00))
        );
    }

    @Test
    void testConsumeBatchUpdateExistingConversion() {
        ConversionEntity entity = new ConversionEntity(
                "conv_102", "tenant_a", "clk_102", "off_101", "aff_101",
                "tx_102", BigDecimal.valueOf(10.00), BigDecimal.valueOf(20.00),
                BigDecimal.valueOf(50.00), "REJECTED", 2L, "CTIT_TOO_FAST", "sub1_fb",
                "FAILED", Instant.now()
        );
        when(conversionMapper.selectById("conv_102")).thenReturn(entity);

        ConsumerRecord<String, ConversionEntity> record =
                new ConsumerRecord<>("affiliate.events.conversion", 0, 1L, "conv_102", entity);

        consumer.consumeBatch(List.of(record));

        verify(conversionMapper, times(1)).updateById(entity);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void testEmptyBatchDoesNotFail() {
        assertDoesNotThrow(() -> consumer.consumeBatch(null));
        assertDoesNotThrow(() -> consumer.consumeBatch(List.of()));
    }
}
