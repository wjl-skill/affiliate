package com.affiliate.platform.event;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

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
}
