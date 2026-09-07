package com.affiliate.platform.ssp;

import com.affiliate.platform.domain.AdSlot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SspYieldAndMediationTest {

    @Test
    void dynamicFloorPriceResolution() {
        DynamicYieldManager manager = new DynamicYieldManager();
        AdSlot slot = new AdSlot("slot-1", "Header Banner", 728, 90, 1.0, true, true, Instant.now());

        // 默认无特定规则 -> 采用广告位配置底价 1.0
        DynamicYieldManager.FloorPriceResult defaultFloor = manager.resolveFloor(slot, "US", 1, 10);
        assertEquals(0, new BigDecimal("1.0").compareTo(defaultFloor.hardFloor()));

        // 添加规则：针对 US 手机流量在晚间 (18-23点)，硬底价提升为 3.50，软底价提升为 5.00
        manager.addRule(new FloorPriceRule("rule-1", "slot-1", "US", 1, 18, 23, new BigDecimal("3.50"), new BigDecimal("5.00")));

        // 晚间 20点 命中规则
        DynamicYieldManager.FloorPriceResult matchedFloor = manager.resolveFloor(slot, "US", 1, 20);
        assertEquals(new BigDecimal("3.50"), matchedFloor.hardFloor());
        assertEquals(new BigDecimal("5.00"), matchedFloor.softFloor());

        // 早间 10点 未命中时段 -> 回退默认
        DynamicYieldManager.FloorPriceResult unmatchTime = manager.resolveFloor(slot, "US", 1, 10);
        assertEquals(0, new BigDecimal("1.0").compareTo(unmatchTime.hardFloor()));
    }

    @Test
    void mediationArbitrationAndBackfill() {
        MediationService mediation = new MediationService();
        String backfillHtml = "<div>Ad: In-House Promotion</div>";

        // 场景 1：有超过硬底价的最高竞价者
        List<MediationService.BidCandidate> candidates = List.of(
                new MediationService.BidCandidate("DSP_A", new BigDecimal("2.00"), "<div>DSP A Ad</div>"),
                new MediationService.BidCandidate("DSP_B", new BigDecimal("4.50"), "<div>DSP B Ad</div>"),
                new MediationService.BidCandidate("DSP_C", new BigDecimal("1.20"), "<div>DSP C Ad</div>")
        );

        MediationService.MediationResult result = mediation.arbitrateHeaderBidding(candidates, new BigDecimal("3.00"), backfillHtml);
        assertFalse(result.isBackfill());
        assertEquals("DSP_B", result.winnerBidderId());
        assertEquals(new BigDecimal("4.50"), result.winningPrice());
        assertEquals("<div>DSP B Ad</div>", result.renderContent());

        // 场景 2：所有出价均低于硬底价 5.00 -> 触发保底填充
        MediationService.MediationResult backfillResult = mediation.arbitrateHeaderBidding(candidates, new BigDecimal("5.00"), backfillHtml);
        assertTrue(backfillResult.isBackfill());
        assertEquals("backfill", backfillResult.winnerBidderId());
        assertEquals(backfillHtml, backfillResult.renderContent());
    }

    @Test
    void prebidParallelHeaderBiddingOrchestration() {
        HeaderBiddingOrchestrator orchestrator = new HeaderBiddingOrchestrator();

        List<HeaderBiddingOrchestrator.DemandPartnerAdapter> adapters = List.of(
                new HeaderBiddingOrchestrator.DemandPartnerAdapter() {
                    public String partnerId() { return "AppLovin"; }
                    public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
                        return new HeaderBiddingOrchestrator.BidOffer("AppLovin", "cr-1", new BigDecimal("3.20"), "<div>AppLovin Ad</div>", "USD");
                    }
                },
                new HeaderBiddingOrchestrator.DemandPartnerAdapter() {
                    public String partnerId() { return "TheTradeDesk"; }
                    public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
                        return new HeaderBiddingOrchestrator.BidOffer("TheTradeDesk", "cr-2", new BigDecimal("4.80"), "<div>TTD Ad</div>", "USD");
                    }
                },
                new HeaderBiddingOrchestrator.DemandPartnerAdapter() {
                    public String partnerId() { return "SlowBidder"; }
                    public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
                        try { Thread.sleep(200); } catch (Exception ignored) {}
                        return new HeaderBiddingOrchestrator.BidOffer("SlowBidder", "cr-3", new BigDecimal("9.99"), "<div>Slow Ad</div>", "USD");
                    }
                }
        );

        // 设定 80ms 超时限制，底价 $2.00
        HeaderBiddingOrchestrator.HeaderBiddingAuctionResult result = orchestrator.orchestrateAuction(
                "slot-hb-1", new BigDecimal("2.00"), adapters, 80
        );

        assertTrue(result.hasWinner());
        assertEquals("TheTradeDesk", result.winningBid().bidderId());
        assertEquals(0, new BigDecimal("4.80").compareTo(result.clearingPrice()));
        assertTrue(result.timedOutBidders() >= 1, "Slow bidder should be timed out");
    }
}
