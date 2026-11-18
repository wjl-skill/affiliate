package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.service.*;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.InMemoryEventPublisher;
import com.affiliate.platform.event.InMemoryOutboxStore;
import com.affiliate.platform.event.KafkaEventStreamBridge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Phase A 验证：RTB Click -> S2S Postback 跨节点归因、网盟事件入湖与 Kafka Loss 桥接测试")
class PhaseACrossPlaneAttributionAndEventBusTest {

    @Test
    @DisplayName("1. S2sPostbackService 解析 Redis 中 rtb_clk_* 会话并向 EventPublisher 发布转化事件")
    @SuppressWarnings("unchecked")
    void testS2sPostbackResolvesRtbClickSessionAndEmitsDomainEvents() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(mockOps);

        String rtbClickId = "rtb_clk_99887766";
        long clickTimeMs = Instant.now().minusSeconds(15).toEpochMilli();
        when(mockOps.get("rtb:click:sess:" + rtbClickId))
                .thenReturn(rtbClickId + "||tenant_a||camp_rtb_99||auc_r_1||3.5||" + clickTimeMs);
        when(mockOps.setIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Boolean.TRUE);

        OfferService offerService = new OfferService();
        offerService.save(new Offer(
                "camp_rtb_99", "tenant_a", "adv_1", "RTB Cross-Plane Offer",
                "https://adv.example.com?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("10.00"), new BigDecimal("16.00"), Offer.Status.ACTIVE,
                100, null, null, Set.of("US"), Set.of(1), null, Instant.now()
        ));

        ClickTrackerService clickTracker = new ClickTrackerService();
        AffiliateAntiFraudEngine antiFraud = new AffiliateAntiFraudEngine();
        antiFraud.setAutoApproveLowRisk(true);
        PublisherPostbackDispatcher dispatcher = new PublisherPostbackDispatcher();

        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        List<DomainEvent<?>> emittedEvents = new ArrayList<>();
        eventPublisher.subscribe(emittedEvents::add);

        clickTracker.setEventPublisher(eventPublisher);
        S2sPostbackService postbackService = new S2sPostbackService(
                clickTracker, offerService, antiFraud, dispatcher,
                null, null, null, mockRedis
        );
        postbackService.setEventPublisher(eventPublisher);

        // 先触发一次普通网盟点击，验证 click.recorded.v1 上报
        clickTracker.trackClick(offerService.find("camp_rtb_99").orElseThrow(), "aff_1",
                "sub_a", null, null, null, null, "8.8.8.8", "Mozilla/5.0", "US", 1);
        assertTrue(emittedEvents.stream().anyMatch(e -> "click.recorded.v1".equals(e.eventType())),
                "网盟点击应向 EventPublisher 发布 click.recorded.v1");

        // 再通过 rtb_clk_* 触发 S2S 转化回传，验证跨 Redis 归因与 conversion.approved.v1 上报
        Conversion conv = postbackService.processPostback(
                rtbClickId, "tx_rtb_cross_001", BigDecimal.ZERO, null, null,
                "8.8.8.8", "Mozilla/5.0 (iPhone)", 1, Instant.now()
        );

        assertEquals(Conversion.Status.APPROVED, conv.status());
        assertEquals("camp_rtb_99", conv.offerId());
        assertEquals("rtb_direct", conv.affiliateId());
        assertEquals(new BigDecimal("10.00"), conv.payout());
        assertTrue(emittedEvents.stream().anyMatch(e -> "conversion.approved.v1".equals(e.eventType())),
                "审批通过的网盟/RTB转化应向 EventPublisher 发布 conversion.approved.v1");
    }

    @Test
    @DisplayName("2. KafkaEventStreamBridge 支持消费 affiliate.auction.loss.v1 并本地分发 auction.loss.v1")
    void testKafkaEventStreamBridgeHandlesAuctionLossTopic() {
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        List<DomainEvent<?>> received = new ArrayList<>();
        eventPublisher.subscribe(received::add);

        KafkaEventStreamBridge bridge = new KafkaEventStreamBridge(eventPublisher, null);
        DomainEvent<Map<String, Object>> lossEvent = DomainEvent.create(
                "auction.loss.v1",
                "tenant_a",
                "auc_loss_bridge_01",
                Map.of("auctionId", "auc_loss_bridge_01", "campaignId", "camp_1", "price", 2.80, "lossReason", 102)
        );

        bridge.onKafkaMessage(lossEvent);
        assertEquals(1L, bridge.getConsumedCount());
        assertEquals(1, received.size());
        assertEquals("auction.loss.v1", received.get(0).eventType());
    }
}
