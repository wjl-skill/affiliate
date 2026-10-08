package com.affiliate.platform.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxDeduplicationAndRelayTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private InMemoryOutboxStore outboxStore;
    private KafkaEventPublisher publisher;
    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        outboxStore = new InMemoryOutboxStore();
        publisher = new KafkaEventPublisher(kafkaTemplate, outboxStore);
        scheduler = new OutboxRelayScheduler(outboxStore, kafkaTemplate, null);
    }

    @Test
    void testDirectSendSuccessMarksOutboxPublishedImmediatelyToAvoidDuplicateRelay() {
        DomainEvent<Map<String, String>> event = DomainEvent.create(
                "campaign.created.v1", "tenant_a", "camp_1", Map.of("key", "val")
        );

        // 模拟直接发送成功
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("affiliate.campaign.created.v1"), eq("camp_1"), eq(event)))
                .thenReturn(future);

        publisher.publish(event);

        // 验证发件箱中此事件已变为 published，pending 队列为空
        assertEquals(0, outboxStore.pending(10).size(), "直发成功后发件箱必须立即标记发布，避免中继双发");

        // 中继调度器触发轮询，此时不应再次发送
        reset(kafkaTemplate);
        scheduler.relay();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void testDirectSendFailureLeavesOutboxPendingForSchedulerToRelay() {
        DomainEvent<Map<String, String>> event = DomainEvent.create(
                "campaign.created.v1", "tenant_a", "camp_2", Map.of("key", "val")
        );

        // 模拟直接发送发生瞬时异常
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka temporary broker disconnect"));
        when(kafkaTemplate.send(eq("affiliate.campaign.created.v1"), eq("camp_2"), eq(event)))
                .thenReturn(failedFuture);

        publisher.publish(event);

        // 验证发件箱中保留为 pending
        assertEquals(1, outboxStore.pending(10).size(), "直发失败必须保留在发件箱中");

        // 中继调度器介入重试
        CompletableFuture<SendResult<String, Object>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("affiliate.campaign.created.v1"), eq("camp_2"), eq(event)))
                .thenReturn(successFuture);

        scheduler.relay();

        // 中继成功后也已标记发布
        assertEquals(0, outboxStore.pending(10).size(), "中继成功后发件箱必须标记发布");
    }
}
