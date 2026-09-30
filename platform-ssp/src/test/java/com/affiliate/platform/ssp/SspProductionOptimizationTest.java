package com.affiliate.platform.ssp;

import com.affiliate.platform.domain.AdSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SspProductionOptimizationTest {

    @Test
    @DisplayName("SSP 头部竞价智能 Top-K 裁剪与自适应熔断器测试")
    void testSmartBidderSelectionAndCircuitBreaker() {
        HeaderBiddingOrchestrator orchestrator = new HeaderBiddingOrchestrator();

        // 创建 10 个 DSP 适配器
        List<HeaderBiddingOrchestrator.DemandPartnerAdapter> adapters = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            final String bidderId = "DSP_" + i;
            final double price = 1.0 + i * 0.5; // DSP_10 出价最高
            adapters.add(new HeaderBiddingOrchestrator.DemandPartnerAdapter() {
                @Override
                public String partnerId() { return bidderId; }
                @Override
                public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
                    return new HeaderBiddingOrchestrator.BidOffer(
                            bidderId, "cr-" + bidderId, BigDecimal.valueOf(price), "<div>Ad</div>", "USD"
                    );
                }
            });
        }

        // 限制最多并发 5 个买家 (Top-K = 5)
        HeaderBiddingOrchestrator.HeaderBiddingAuctionResult res = orchestrator.orchestrateAuction(
                "slot-1", new BigDecimal("1.00"), adapters, 100, 5
        );

        assertTrue(res.hasWinner());
        assertEquals(5, res.respondedBidders(), "应仅向裁剪后的 Top 5 买家发送请求");

        // 模拟一个持续超时的劣质 DSP
        HeaderBiddingOrchestrator.DemandPartnerAdapter failingAdapter = new HeaderBiddingOrchestrator.DemandPartnerAdapter() {
            @Override
            public String partnerId() { return "Flaky_DSP"; }
            @Override
            public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
                try { Thread.sleep(150); } catch (Exception ignored) {}
                return new HeaderBiddingOrchestrator.BidOffer("Flaky_DSP", "cr", new BigDecimal("10.0"), "ad", "USD");
            }
        };

        List<HeaderBiddingOrchestrator.DemandPartnerAdapter> flakyList = List.of(failingAdapter);

        // 连续 5 次超时，触发熔断
        for (int i = 0; i < 5; i++) {
            orchestrator.orchestrateAuction("slot-1", BigDecimal.ZERO, flakyList, 30);
        }

        HeaderBiddingOrchestrator.BidderMetrics metrics = orchestrator.getMetrics("Flaky_DSP");
        assertNotNull(metrics);
        assertTrue(metrics.consecutiveFailures.get() >= 5);
        assertTrue(metrics.circuitOpenUntil.get() > System.currentTimeMillis(), "连续失败应触发熔断冷却");

        // 再次发起拍卖，该买家被熔断器直接跳过，响应为 0，不再发起网络请求
        HeaderBiddingOrchestrator.HeaderBiddingAuctionResult skippedRes = orchestrator.orchestrateAuction(
                "slot-1", BigDecimal.ZERO, flakyList, 30
        );
        assertEquals(0, skippedRes.respondedBidders());
    }

    @Test
    @DisplayName("自适应动态底价根据流拍与填充率双向自适应微调测试")
    void testAdaptiveYieldOptimizationFeedback() {
        DynamicYieldManager yieldManager = new DynamicYieldManager();
        AdSlot slot = new AdSlot("slot-news", "News Sidebar", 300, 250, 2.0, true, true, Instant.now());

        // 初始无历史数据，返回基准底价 $2.00
        DynamicYieldManager.FloorPriceResult base = yieldManager.resolveAdaptiveFloor(slot, "US", 1, 14);
        assertEquals(0, new BigDecimal("2.0000").compareTo(base.hardFloor()));

        // 模拟流拍严重场景：连续 10 次拍卖均流拍（Fill Rate = 0%）
        for (int i = 0; i < 10; i++) {
            yieldManager.recordAuctionOutcome("slot-news", false, 0.0, 3);
        }

        DynamicYieldManager.FloorPriceResult lowFillResult = yieldManager.resolveAdaptiveFloor(slot, "US", 1, 14);
        // 底价自适应下调以促成成交，最大折扣至 0.70x -> 2.00 * 0.70 = 1.4000
        assertEquals(0, new BigDecimal("1.4000").compareTo(lowFillResult.hardFloor()));
        assertTrue(lowFillResult.hardFloor().compareTo(base.hardFloor()) < 0);

        // 清理并模拟需求极度旺盛场景：连续 20 次拍卖全部成交（Fill Rate = 100%）
        yieldManager.clearStats();
        for (int i = 0; i < 20; i++) {
            yieldManager.recordAuctionOutcome("slot-news", true, 4.5, 6);
        }

        DynamicYieldManager.FloorPriceResult highDemandResult = yieldManager.resolveAdaptiveFloor(slot, "US", 1, 14);
        // 底价自适应抬升，最高增幅 1.50x -> 2.00 * 1.50 = 3.0000
        assertEquals(0, new BigDecimal("3.0000").compareTo(highDemandResult.hardFloor()));
        assertTrue(highDemandResult.hardFloor().compareTo(base.hardFloor()) > 0);
    }

    @Test
    @DisplayName("SSP 统一 4 层混合竞价仲裁流水线测试 (PG, PMP, Header Bidding, Backfill)")
    void testUnifiedAuctionArbitrationPipeline() {
        MediationService mediation = new MediationService();
        DynamicYieldManager.FloorPriceResult floor = new DynamicYieldManager.FloorPriceResult(
                new BigDecimal("2.00"), new BigDecimal("4.00")
        );
        String backfillHtml = "<div>In-House Backfill Ad</div>";

        // 场景 1: 存在有效 Tier 1 保量合约 (PG) -> 直接胜出
        MediationService.GuaranteedContract pgContract = new MediationService.GuaranteedContract(
                "contract-nike", "Nike", new BigDecimal("10.00"), "<div>Nike Guaranteed Ad</div>"
        );
        MediationService.PreferredDealBid deal = new MediationService.PreferredDealBid(
                "deal-1", "DSP_PMP", new BigDecimal("5.00"), "<div>PMP Ad</div>"
        );
        List<MediationService.BidCandidate> hbBids = List.of(
                new MediationService.BidCandidate("DSP_Open", new BigDecimal("6.00"), "<div>Open Ad</div>")
        );

        MediationService.MediationResult pgResult = mediation.arbitrateUnifiedAuction(
                pgContract, List.of(deal), hbBids, floor, backfillHtml
        );
        assertEquals(MediationService.AuctionTier.PROGRAMMATIC_GUARANTEED, pgResult.winningTier());
        assertEquals("Guaranteed_Nike", pgResult.winnerBidderId());
        assertEquals("contract-nike", pgResult.dealOrContractId());
        assertFalse(pgResult.isBackfill());

        // 场景 2: 无 PG 合约，存在满足底价的 Tier 2 PMP Preferred Deal -> PMP 胜出
        MediationService.MediationResult pmpResult = mediation.arbitrateUnifiedAuction(
                null, List.of(deal), hbBids, floor, backfillHtml
        );
        assertEquals(MediationService.AuctionTier.PREFERRED_DEAL, pmpResult.winningTier());
        assertEquals("DSP_PMP", pmpResult.winnerBidderId());
        assertEquals(new BigDecimal("5.00"), pmpResult.winningPrice());

        // 场景 3: 无 PG、无 PMP，进入 Tier 3 Open Header Bidding
        // 最高出价 $6.00 高于软底价 $4.00 -> 按第一价格自身出清
        MediationService.MediationResult hbResult = mediation.arbitrateUnifiedAuction(
                null, List.of(), hbBids, floor, backfillHtml
        );
        assertEquals(MediationService.AuctionTier.OPEN_HEADER_BIDDING, hbResult.winningTier());
        assertEquals("DSP_Open", hbResult.winnerBidderId());
        assertEquals(new BigDecimal("6.00"), hbResult.winningPrice());

        // 场景 4: 介于硬底价 $2.00 与软底价 $4.00 之间，次高价为 $2.50
        List<MediationService.BidCandidate> midBids = List.of(
                new MediationService.BidCandidate("DSP_A", new BigDecimal("3.50"), "<div>A</div>"),
                new MediationService.BidCandidate("DSP_B", new BigDecimal("2.50"), "<div>B</div>")
        );
        MediationService.MediationResult midResult = mediation.arbitrateUnifiedAuction(
                null, List.of(), midBids, floor, backfillHtml
        );
        assertEquals(MediationService.AuctionTier.OPEN_HEADER_BIDDING, midResult.winningTier());
        assertEquals("DSP_A", midResult.winnerBidderId());
        assertEquals(new BigDecimal("2.50"), midResult.winningPrice());

        // 场景 5: 全网出价低于硬底价 $2.00 -> 触发 Tier 4 Backfill 兜底
        List<MediationService.BidCandidate> lowBids = List.of(
                new MediationService.BidCandidate("DSP_Low", new BigDecimal("1.20"), "<div>Low</div>")
        );
        MediationService.MediationResult backfillResult = mediation.arbitrateUnifiedAuction(
                null, List.of(), lowBids, floor, backfillHtml
        );
        assertTrue(backfillResult.isBackfill());
        assertEquals(MediationService.AuctionTier.HOUSE_BACKFILL, backfillResult.winningTier());
        assertEquals(backfillHtml, backfillResult.renderContent());
    }
}
