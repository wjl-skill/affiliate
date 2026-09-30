package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.SmartLink;
import com.affiliate.platform.affiliate.service.OfferService;
import com.affiliate.platform.affiliate.service.SubIdAnalyticsService;
import com.affiliate.platform.affiliate.service.TdsRouter;
import com.affiliate.platform.entity.SubIdStatsEntity;
import com.affiliate.platform.mapper.SubIdStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 三阶段深度优化测试（TDS 平滑轮询与平局防饿死均摊、降级熔断、Sub-ID 流式原子 Upsert）
 */
class Phase3DeepeningTest {

    private OfferService offerService;
    private TdsRouter tdsRouter;
    private SubIdStatsMapper statsMapper;
    private SubIdAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        offerService = new OfferService();
        tdsRouter = new TdsRouter(offerService);
        statsMapper = Mockito.mock(SubIdStatsMapper.class);
        analyticsService = new SubIdAnalyticsService(statsMapper);
    }

    @Test
    @DisplayName("测试 TDS SmartLink ROUND_ROBIN 策略平滑轮询均摊分流")
    void testTdsRoundRobinRouting() {
        Offer offA = new Offer("off-rr-1", "t1", "adv-1", "Offer 1", "https://example.com/1",
                Offer.PayoutType.CPA, new BigDecimal("10.00"), new BigDecimal("15.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        Offer offB = new Offer("off-rr-2", "t1", "adv-1", "Offer 2", "https://example.com/2",
                Offer.PayoutType.CPA, new BigDecimal("12.00"), new BigDecimal("18.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        Offer offC = new Offer("off-rr-3", "t1", "adv-1", "Offer 3", "https://example.com/3",
                Offer.PayoutType.CPA, new BigDecimal("14.00"), new BigDecimal("20.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());

        offerService.save(offA);
        offerService.save(offB);
        offerService.save(offC);

        SmartLink link = new SmartLink("link-rr-100", "t1", "Test RR Link", "Dating",
                List.of("off-rr-1", "off-rr-2", "off-rr-3"),
                SmartLink.RoutingStrategy.ROUND_ROBIN, null, Instant.now());

        Map<String, Integer> hitCounts = new HashMap<>();
        for (int i = 0; i < 90; i++) {
            Offer routed = tdsRouter.route(link, "US", 1, "2026-09-30");
            assertNotNull(routed);
            hitCounts.put(routed.id(), hitCounts.getOrDefault(routed.id(), 0) + 1);
        }

        // 3 个候选均分 90 次流量，各得 30 次
        assertEquals(30, hitCounts.get("off-rr-1"));
        assertEquals(30, hitCounts.get("off-rr-2"));
        assertEquals(30, hitCounts.get("off-rr-3"));
    }

    @Test
    @DisplayName("测试 TDS HIGHEST_EPC 策略下多个相同 EPC 候选的平局均摊防饿死")
    void testTdsHighestEpcTieBreaking() {
        Offer offA = new Offer("off-epc-1", "t1", "adv-1", "Offer 1", "https://example.com/1",
                Offer.PayoutType.CPA, new BigDecimal("10.00"), new BigDecimal("15.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        Offer offB = new Offer("off-epc-2", "t1", "adv-1", "Offer 2", "https://example.com/2",
                Offer.PayoutType.CPA, new BigDecimal("12.00"), new BigDecimal("18.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        Offer offC = new Offer("off-epc-3", "t1", "adv-1", "Offer 3", "https://example.com/3",
                Offer.PayoutType.CPA, new BigDecimal("8.00"), new BigDecimal("12.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());

        offerService.save(offA);
        offerService.save(offB);
        offerService.save(offC);

        // off-epc-1 和 off-epc-2 同为最高 EPC 2.50，off-epc-3 为 1.00
        tdsRouter.updateOfferEpc("off-epc-1", new BigDecimal("2.50"));
        tdsRouter.updateOfferEpc("off-epc-2", new BigDecimal("2.50"));
        tdsRouter.updateOfferEpc("off-epc-3", new BigDecimal("1.00"));

        SmartLink link = new SmartLink("link-epc-tie", "t1", "Test EPC Link", "Finance",
                List.of("off-epc-1", "off-epc-2", "off-epc-3"),
                SmartLink.RoutingStrategy.HIGHEST_EPC, null, Instant.now());

        Map<String, Integer> hitCounts = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            Offer routed = tdsRouter.route(link, "US", 1, "2026-09-30");
            assertNotNull(routed);
            hitCounts.put(routed.id(), hitCounts.getOrDefault(routed.id(), 0) + 1);
        }

        // 两个最高 EPC 候选各分 20 次，低 EPC 候选分 0 次
        assertEquals(20, hitCounts.get("off-epc-1"));
        assertEquals(20, hitCounts.get("off-epc-2"));
        assertNull(hitCounts.get("off-epc-3"));
    }

    @Test
    @DisplayName("测试 TDS 候选 Offer 临时熔断与降级剔除机制")
    void testTdsOfferDegradedCircuitBreaker() {
        Offer offA = new Offer("off-brk-1", "t1", "adv-1", "Offer 1", "https://example.com/1",
                Offer.PayoutType.CPA, new BigDecimal("10.00"), new BigDecimal("15.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        Offer offB = new Offer("off-brk-2", "t1", "adv-1", "Offer 2", "https://example.com/2",
                Offer.PayoutType.CPA, new BigDecimal("12.00"), new BigDecimal("18.00"),
                Offer.Status.ACTIVE, 0, null, null, Set.of("US"), Set.of(1), null, Instant.now());

        offerService.save(offA);
        offerService.save(offB);

        tdsRouter.updateOfferEpc("off-brk-1", new BigDecimal("5.00"));
        tdsRouter.updateOfferEpc("off-brk-2", new BigDecimal("3.00"));

        SmartLink link = new SmartLink("link-brk", "t1", "Test Breaker Link", "Ecom",
                List.of("off-brk-1", "off-brk-2"),
                SmartLink.RoutingStrategy.HIGHEST_EPC, null, Instant.now());

        // 正常情况下命中最高 EPC 的 off-brk-1
        Offer routedNormal = tdsRouter.route(link, "US", 1, "2026-09-30");
        assertEquals("off-brk-1", routedNormal.id());

        // 模拟 off-brk-1 出现连续 5xx 触发熔断 60s
        tdsRouter.markOfferDegraded("off-brk-1", Duration.ofSeconds(60));
        assertTrue(tdsRouter.isOfferDegraded("off-brk-1"));

        // 再次路由，自动剔除 off-brk-1，由次高 EPC 的 off-brk-2 接管
        Offer routedDegraded = tdsRouter.route(link, "US", 1, "2026-09-30");
        assertEquals("off-brk-2", routedDegraded.id());
    }

    @Test
    @DisplayName("测试 SubIdAnalyticsService 数据库原子增量写入调用")
    void testSubIdAnalyticsServiceAtomicUpsert() {
        analyticsService.recordClick("aff_888", "campaign_meta");

        ArgumentCaptor<SubIdStatsEntity> captor = ArgumentCaptor.forClass(SubIdStatsEntity.class);
        verify(statsMapper, times(1)).upsertIncremental(captor.capture());

        SubIdStatsEntity capturedClick = captor.getValue();
        assertEquals("aff_888", capturedClick.getAffiliateId());
        assertEquals("campaign_meta", capturedClick.getSub1());
        assertEquals(1L, capturedClick.getClicks());
        assertEquals(0L, capturedClick.getConversions());

        // 模拟转化回传
        analyticsService.recordConversion("aff_888", "campaign_meta", new BigDecimal("25.00"), new BigDecimal("35.00"));
        verify(statsMapper, times(2)).upsertIncremental(captor.capture());

        SubIdStatsEntity capturedConv = captor.getValue();
        assertEquals("aff_888", capturedConv.getAffiliateId());
        assertEquals("campaign_meta", capturedConv.getSub1());
        assertEquals(0L, capturedConv.getClicks());
        assertEquals(1L, capturedConv.getConversions());
        assertEquals(new BigDecimal("25.00"), capturedConv.getTotalPayout());
        assertEquals(new BigDecimal("35.00"), capturedConv.getTotalRevenue());
    }
}
