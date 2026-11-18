package com.affiliate.platform.reporting.olap;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.InMemoryEventPublisher;
import com.affiliate.platform.event.InMemoryOutboxStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase B 验证：OLAP 有界维度桶防 OOM 与微批高水位刷新测试")
class PhaseBOlapBoundedCubeTest {

    @Test
    @DisplayName("验证 OLAP 订阅器高基数维度摄入与物化视图同步")
    void testBoundedCubeIngestionAndFlush() {
        ClickHouseOlapClient olapClient = new ClickHouseOlapClient();
        InMemoryEventPublisher publisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        OlapEventStreamSubscriber subscriber = new OlapEventStreamSubscriber(olapClient, publisher);
        subscriber.initSubscription();

        for (int i = 0; i < 25; i++) {
            publisher.publish(DomainEvent.create(
                    "click.recorded.v1",
                    "tenant_olap",
                    "clk_" + i,
                    Map.of("offerId", "off_1", "campaignId", "off_1", "country", "US", "sub1", "sub_" + i)
            ));
        }
        int flushed = subscriber.flushBatch();
        assertEquals(25, flushed);
        assertEquals(0L, subscriber.getDroppedEvents());
        assertEquals(1L, subscriber.getTotalFlushedBatches());
    }
}
