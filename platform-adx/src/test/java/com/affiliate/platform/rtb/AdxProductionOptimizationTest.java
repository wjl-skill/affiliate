package com.affiliate.platform.rtb;

import com.affiliate.platform.domain.Auction;
import com.affiliate.platform.repository.InMemoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AdxProductionOptimizationTest {

    @Test
    @DisplayName("PMP 私有交易白名单与底价撮合匹配测试")
    void testPmpDealMatcher() {
        PmpDealMatcher matcher = new PmpDealMatcher();

        // 注册 PG 保量 Deal
        PmpDealMatcher.RegisteredDeal pgDeal = new PmpDealMatcher.RegisteredDeal(
                "deal-nike-pg", "Nike", PmpDealMatcher.DealType.PROGRAMMATIC_GUARANTEED,
                new BigDecimal("5.00"), Set.of("seat-dv360", "seat-ttd"), true
        );
        matcher.registerDeal(pgDeal);

        // 场景 1: 白名单席位 + 达到底价 -> 成功匹配，最高优先级
        PmpDealMatcher.DealMatchResult res1 = matcher.matchDeal("deal-nike-pg", "seat-dv360", new BigDecimal("5.50"));
        assertTrue(res1.isMatched());
        assertEquals(100, res1.priority());

        // 场景 2: 未授权席位出价 -> 拒绝
        PmpDealMatcher.DealMatchResult res2 = matcher.matchDeal("deal-nike-pg", "seat-unauthorized", new BigDecimal("10.00"));
        assertFalse(res2.isMatched());

        // 场景 3: 出价低于 Deal Floor -> 拒绝
        PmpDealMatcher.DealMatchResult res3 = matcher.matchDeal("deal-nike-pg", "seat-dv360", new BigDecimal("4.80"));
        assertFalse(res3.isMatched());
    }

    @Test
    @DisplayName("多席位 DSP 并发撮合与 Vickrey 第二价格出清测试")
    void testMultiSeatExchangeAuction() {
        PmpDealMatcher matcher = new PmpDealMatcher();
        MultiSeatAuctionExchange exchange = new MultiSeatAuctionExchange(matcher);

        // 注册两个 DSP 席位
        exchange.registerSeat(new MultiSeatAuctionExchange.SeatBidderAdapter() {
            @Override
            public String seatId() { return "seat-dsp-a"; }
            @Override
            public Optional<OpenRtb.BidResponse> requestBids(OpenRtb.BidRequest request) {
                OpenRtb.Bid bid = new OpenRtb.Bid("bid-1", "imp-1", 4.50, "adm-a", "ad-a.com", "nurl-a", "cr-a");
                return Optional.of(new OpenRtb.BidResponse("req-1", List.of(new OpenRtb.SeatBid(List.of(bid), "seat-dsp-a")), "USD"));
            }
        });

        exchange.registerSeat(new MultiSeatAuctionExchange.SeatBidderAdapter() {
            @Override
            public String seatId() { return "seat-dsp-b"; }
            @Override
            public Optional<OpenRtb.BidResponse> requestBids(OpenRtb.BidRequest request) {
                OpenRtb.Bid bid = new OpenRtb.Bid("bid-2", "imp-1", 3.00, "adm-b", "ad-b.com", "nurl-b", "cr-b");
                return Optional.of(new OpenRtb.BidResponse("req-1", List.of(new OpenRtb.SeatBid(List.of(bid), "seat-dsp-b")), "USD"));
            }
        });

        OpenRtb.BidRequest req = new OpenRtb.BidRequest(
                "req-1",
                List.of(new OpenRtb.Imp("imp-1", null, null, 0.0, "USD", null)),
                null, null, null, 20
        );

        // 第二价拍卖出清：胜出者为 DSP-A (4.50)，清算价为次高价 (3.00) + 0.01 = 3.01
        MultiSeatAuctionExchange.ExchangeClearingResult result = exchange.conductExchangeAuction(
                req, new BigDecimal("1.00"), true
        );

        assertTrue(result.hasWinner());
        assertEquals("seat-dsp-a", result.winningSeatId());
        assertEquals(new BigDecimal("3.01"), result.clearingPrice());
        assertEquals("SECOND_PRICE_VICKREY", result.clearingMode());
    }

    @Test
    @DisplayName("Disruptor 异步环形缓冲账本微批刷盘测试")
    void testAuctionDisruptorLedger() throws InterruptedException {
        InMemoryRepository<Auction> repo = new InMemoryRepository<>(Auction::id);
        AuctionDisruptorLedger ledger = new AuctionDisruptorLedger(repo, 10_000);

        int count = 100;
        for (int i = 0; i < count; i++) {
            Auction a = new Auction("auc-" + i, "req-" + i, "slot-1", "cr-1", 2.50, "USD", "adv-1", Instant.now());
            assertTrue(ledger.recordAsync(a));
        }

        assertEquals(count, ledger.totalEnqueued.get());

        // 等待微批后台处理刷盘
        int waits = 0;
        while (repo.findAll().size() < count && waits < 20) {
            Thread.sleep(25);
            waits++;
        }

        assertEquals(count, repo.findAll().size());
        assertEquals(count, ledger.totalPersisted.get());

        ledger.shutdown();
    }

    @Test
    @DisplayName("自适应过载保护 (Load Shedder) 削峰与 PMP 优先级测试")
    void testAdaptiveLoadShedder() {
        // 设置最大并发槽位为 5
        RtbAdaptiveLoadShedder shedder = new RtbAdaptiveLoadShedder(5);

        OpenRtb.BidRequest normalReq = new OpenRtb.BidRequest(
                "req-normal",
                List.of(new OpenRtb.Imp("imp-1", null, null, 0.0, "USD", null)),
                null, null, null, 20
        );

        OpenRtb.BidRequest pmpReq = new OpenRtb.BidRequest(
                "req-pmp",
                List.of(new OpenRtb.Imp("imp-2", null, null, 0.0, "USD",
                        new OpenRtb.Pmp(1, List.of(new OpenRtb.Deal("deal-1", 5.0, "USD", null, null))))),
                null, null, null, 20
        );

        // 模拟填满槽位
        for (int i = 0; i < 5; i++) {
            shedder.acquire();
        }

        // 普通请求在满载时触发削峰丢弃
        assertTrue(shedder.shouldShed(normalReq));

        // PMP 私有交易请求在轻度过载下享受特权放行
        assertFalse(shedder.shouldShed(pmpReq));

        // 释放一个槽位
        shedder.release();
        assertFalse(shedder.shouldShed(normalReq));
    }
}
