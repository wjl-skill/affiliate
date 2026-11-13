package com.affiliate.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaEventStreamBridgeTest {

    @Test
    @DisplayName("验证 KafkaEventStreamBridge 跨进程消费 JSON 事件并幂等去重分发至本地订阅者")
    void testCrossJvmBridgeDeserializationAndDeduplication() throws Exception {
        InMemoryOutboxStore outboxStore = new InMemoryOutboxStore();
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(outboxStore);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        List<DomainEvent<?>> received = new ArrayList<>();
        eventPublisher.subscribe(received::add);

        KafkaEventStreamBridge bridge = new KafkaEventStreamBridge(eventPublisher, objectMapper);
        DomainEvent<Map<String, Object>> winEvent = DomainEvent.create(
                "auction.win.v1",
                "tenant_rtb",
                "auc_888",
                Map.of("campaignId", "camp_1", "slotId", "slot_1", "price", 4.50)
        );
        String jsonPayload = objectMapper.writeValueAsString(winEvent);

        // 1. 首次收到 JSON 格式 Kafka 事件 -> 分发至本地订阅者，且不重复写 Outbox
        bridge.onKafkaMessage(jsonPayload);
        assertEquals(1, received.size());
        assertEquals("auction.win.v1", received.get(0).eventType());
        assertEquals(0, eventPublisher.size(), "dispatchLocal must not re-append to publisher queue");
        assertEquals(1L, bridge.getConsumedCount());

        // 2. 重复投递相同 eventId -> 自动去重
        bridge.onKafkaMessage(winEvent);
        assertEquals(1, received.size());
        assertEquals(1L, bridge.getDeduplicatedCount());
    }
}
