package com.affiliate.platform.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxStoreTest {

    @Test
    void testInMemoryOutboxLifecycle() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        DomainEvent<Map<String, String>> event1 = DomainEvent.create("test.event.v1", "t1", "agg1", Map.of("key", "val1"));
        DomainEvent<Map<String, String>> event2 = DomainEvent.create("test.event.v1", "t1", "agg2", Map.of("key", "val2"));

        store.append(event1);
        store.append(event2);

        List<DomainEvent<?>> pending = store.pending(10);
        assertEquals(2, pending.size());

        // Mark one as published
        store.markPublished(event1.eventId());

        List<DomainEvent<?>> remaining = store.pending(10);
        assertEquals(1, remaining.size());
        assertEquals(event2.eventId(), remaining.get(0).eventId());
    }

    @Test
    @DisplayName("发件箱多次失败达到阈值沉降至死信表，重放后恢复待投递")
    void testDeadLetterThresholdAndReplay() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        DomainEvent<Map<String, String>> event = DomainEvent.create(
                "order.settled.v1", "tenant_007", "ord_999", Map.of("amount", "299.00")
        );
        UUID eventId = event.eventId();
        store.append(event);

        assertEquals(1, store.pending(10).size());
        assertEquals(0, store.getDeadLetters("tenant_007", 10).size());

        // 模拟前 4 次重试失败，仍留在 pending 列表中
        for (int i = 1; i <= 4; i++) {
            store.recordFailure(eventId, "Kafka timeout attempt " + i);
            assertEquals(1, store.pending(10).size(), "第 " + i + " 次失败应保留在待投递列表");
            assertEquals(0, store.getDeadLetters("tenant_007", 10).size());
        }

        // 第 5 次失败，达到死信沉降阈值
        store.recordFailure(eventId, "Kafka broker fatal partition failure");
        assertEquals(0, store.pending(10).size(), "达到重试上限必须移出 pending 队列，解除热点阻塞");

        List<EventOutboxDeadEntity> deadLetters = store.getDeadLetters("tenant_007", 10);
        assertEquals(1, deadLetters.size(), "死信表中必须有且仅有 1 条死信记录");
        EventOutboxDeadEntity dead = deadLetters.get(0);
        assertEquals(eventId, dead.getId());
        assertEquals("tenant_007", dead.getTenantId());
        assertEquals("Kafka broker fatal partition failure", dead.getLastError());
        assertTrue(dead.getAttempts() >= 5);
        assertNull(dead.getReplayedAt());

        // 运维触发 DLT 重放
        boolean replayed = store.replayDeadLetter(eventId);
        assertTrue(replayed);

        // 重放后放回 pending 待投递
        List<DomainEvent<?>> restoredPending = store.pending(10);
        assertEquals(1, restoredPending.size());
        assertEquals(eventId, restoredPending.get(0).eventId());

        // 死信标记为已重放
        assertNotNull(dead.getReplayedAt());
    }
}
