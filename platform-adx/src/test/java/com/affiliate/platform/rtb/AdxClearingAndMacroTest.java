package com.affiliate.platform.rtb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdxClearingAndMacroTest {

    @Test
    void firstPriceAuctionClearing() {
        AuctionClearingEngine engine = new AuctionClearingEngine();

        List<AuctionClearingEngine.CandidateBid> bids = List.of(
                new AuctionClearingEngine.CandidateBid("dsp-1", "b-1", 3.50, null),
                new AuctionClearingEngine.CandidateBid("dsp-2", "b-2", 5.20, null),
                new AuctionClearingEngine.CandidateBid("dsp-3", "b-3", 2.00, null)
        );

        AuctionClearingEngine.ClearingResult res = engine.clear(bids, 1.00, AuctionClearingEngine.AuctionType.FIRST_PRICE, null);
        assertTrue(res.hasWinner());
        assertEquals("dsp-2", res.winner().bidderId());
        assertEquals(5.20, res.clearingPrice(), 0.001);
    }

    @Test
    void secondPriceVickreyClearing() {
        AuctionClearingEngine engine = new AuctionClearingEngine();

        List<AuctionClearingEngine.CandidateBid> bids = List.of(
                new AuctionClearingEngine.CandidateBid("dsp-1", "b-1", 3.50, null),
                new AuctionClearingEngine.CandidateBid("dsp-2", "b-2", 5.20, null),
                new AuctionClearingEngine.CandidateBid("dsp-3", "b-3", 2.00, null)
        );

        // 最高 5.20，次高 3.50 -> 清算价 = 3.50 + 0.01 = 3.51
        AuctionClearingEngine.ClearingResult res = engine.clear(bids, 1.00, AuctionClearingEngine.AuctionType.SECOND_PRICE, null);
        assertTrue(res.hasWinner());
        assertEquals("dsp-2", res.winner().bidderId());
        assertEquals(3.51, res.clearingPrice(), 0.001);

        // 若次高低于底价 4.00 -> 清算价不低于底价 4.00
        AuctionClearingEngine.ClearingResult floorBound = engine.clear(bids, 4.00, AuctionClearingEngine.AuctionType.SECOND_PRICE, null);
        assertEquals(4.00, floorBound.clearingPrice(), 0.001);
    }

    @Test
    void pmpDealPriorityClearing() {
        AuctionClearingEngine engine = new AuctionClearingEngine();

        List<AuctionClearingEngine.CandidateBid> bids = List.of(
                new AuctionClearingEngine.CandidateBid("open-dsp", "b-open", 8.00, null),
                new AuctionClearingEngine.CandidateBid("pmp-dsp", "b-deal", 4.50, "deal-nike-vip")
        );

        // 指定私有 deal-nike-vip：虽然公开出价高达 8.00，但私有 deal 优先撮合成交
        AuctionClearingEngine.ClearingResult res = engine.clear(bids, 1.00, AuctionClearingEngine.AuctionType.SECOND_PRICE, "deal-nike-vip");
        assertTrue(res.hasWinner());
        assertEquals("pmp-dsp", res.winner().bidderId());
        assertEquals(4.50, res.clearingPrice(), 0.001);
    }

    @Test
    void openRtbMacroReplacer() {
        String nurlTemplate = "https://adx.com/win?aid=${AUCTION_ID}&bid=${AUCTION_BID_ID}&price=${AUCTION_PRICE}&cur=${AUCTION_CURRENCY}";
        String rendered = MacroReplacer.replaceNurl(nurlTemplate, "auc-101", "bid-202", "imp-1", 3.85, "USD");

        assertEquals("https://adx.com/win?aid=auc-101&bid=bid-202&price=3.85&cur=USD", rendered);
    }

    @Test
    void testFirstPriceBidShading() {
        BidShadingCalculator calculator = new BidShadingCalculator();

        // 场景 1: 冷启动折价模型 (最高出价 $5.00，底价 $1.00)
        // 期望节省率在 15%~30% 之间，出价介于底价与原价之间
        BidShadingCalculator.BidShadingResult coldStart = calculator.calculateShadedBid(
                "slot-native-1", new java.math.BigDecimal("5.00"), new java.math.BigDecimal("1.00")
        );
        assertTrue(coldStart.shadedBid().compareTo(new java.math.BigDecimal("1.00")) >= 0);
        assertTrue(coldStart.shadedBid().compareTo(new java.math.BigDecimal("5.00")) < 0);
        assertTrue(coldStart.estimatedSavingsRatio().doubleValue() > 0.15);

        // 场景 2: 注入 5 次历史获胜出清样本 (均价约为 $2.50)
        for (int i = 0; i < 5; i++) {
            calculator.recordClearingFeedback("slot-native-1", new java.math.BigDecimal("2.50"));
        }

        // 经样本训练后计算：出价应平滑贴近获胜估算价 (约 2.50 * 1.10 = 2.75)，相比 5.00 原出价节省约 45%
        BidShadingCalculator.BidShadingResult trained = calculator.calculateShadedBid(
                "slot-native-1", new java.math.BigDecimal("5.00"), new java.math.BigDecimal("1.00")
        );
        assertEquals(0, new java.math.BigDecimal("2.7500").compareTo(trained.shadedBid()));
        assertEquals(0, new java.math.BigDecimal("0.4500").compareTo(trained.estimatedSavingsRatio()));
    }
}
