package com.affiliate.platform.ssp;

import com.affiliate.platform.domain.AdSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3 SSP 升级验证：EWMA 指数衰减动态底价、软硬底价双轨出清与端到端统一变现流水线测试")
class Phase3SspYieldAndHeaderBiddingUpgradeTest {

    @Test
    @DisplayName("1. 验证 EWMA 指数滑动衰减动态底价与软硬底价双轨混合出清定价")
    void testEwmaAdaptiveFloorAndHybridClearingPrice() {
        DynamicYieldManager yieldManager = new DynamicYieldManager();
        AdSlot slot = new AdSlot("slot_video_01", "Pre-roll Video", 640, 360, 3.00, true, true, Instant.now());

        yieldManager.addRule(new FloorPriceRule(
                "rule_us_video", "slot_video_01", "US", 1, 0, 23,
                new BigDecimal("3.00"), new BigDecimal("5.00")
        ));

        // 模拟近期高填充率且高买方竞争 (5 个买方并发竞价，100% 成交)
        for (int i = 0; i < 12; i++) {
            yieldManager.recordAuctionOutcome("slot_video_01", true, 6.20, 5);
        }

        DynamicYieldManager.FloorPriceResult ewmaFloor = yieldManager.resolveEwmaAdaptiveFloor(slot, "US", 1, 20);
        assertTrue(ewmaFloor.hardFloor().compareTo(new BigDecimal("3.00")) > 0);
        assertTrue(ewmaFloor.softFloor().compareTo(ewmaFloor.hardFloor()) > 0, "高竞争场景下软底价应高于硬底价");

        // 验证软硬底价双轨出清规则
        DynamicYieldManager.FloorPriceResult fixedFloor = new DynamicYieldManager.FloorPriceResult(
                new BigDecimal("2.0000"), new BigDecimal("4.0000")
        );
        // 1) 低于硬底价 ($1.50 < $2.00) -> 流拍 ($0)
        assertEquals(0, BigDecimal.ZERO.compareTo(
                yieldManager.calculateHybridClearingPrice(fixedFloor, new BigDecimal("1.50"), new BigDecimal("1.20"))));
        // 2) 介于硬底价与软底价之间 ($3.50 in [$2.00, $4.00)) -> 按 max(hardFloor, secondBid) = $2.8000 柔化出清
        assertEquals(new BigDecimal("2.8000"),
                yieldManager.calculateHybridClearingPrice(fixedFloor, new BigDecimal("3.50"), new BigDecimal("2.80")));
        // 3) 高于软底价 ($4.80 >= $4.00) -> 按第一价格 $4.8000 直接出清
        assertEquals(new BigDecimal("4.8000"),
                yieldManager.calculateHybridClearingPrice(fixedFloor, new BigDecimal("4.80"), new BigDecimal("3.10")));
    }

    @Test
    @DisplayName("2. 验证携带完整流量上下文的 Header Bidding 与 SSP 端到端统一变现流水线闭环")
    void testContextualHeaderBiddingAndUnifiedYieldPipeline() {
        DynamicYieldManager yieldManager = new DynamicYieldManager();
        HeaderBiddingOrchestrator orchestrator = new HeaderBiddingOrchestrator();
        MediationService mediationService = new MediationService();

        AdSlot slot = new AdSlot("slot_native_feed", "Native Feed", 300, 250, 2.00, true, true, Instant.now());

        // 创建能够感知 HeaderBidRequestContext（根据高价值受众标签动态加价）的智能 DSP 适配器
        HeaderBiddingOrchestrator.DemandPartnerAdapter contextAwareDsp = new HeaderBiddingOrchestrator.DemandPartnerAdapter() {
            @Override
            public String partnerId() {
                return "DSP_Contextual_AI";
            }

            @Override
            public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
                return new HeaderBiddingOrchestrator.BidOffer(partnerId(), "cr_base", new BigDecimal("2.20"), "<div>Base</div>", "USD");
            }

            @Override
            public HeaderBiddingOrchestrator.BidOffer requestBid(HeaderBiddingOrchestrator.HeaderBidRequestContext context) {
                boolean isVip = context.userSegments() != null && context.userSegments().contains("whale_purchaser");
                BigDecimal bidPrice = isVip ? new BigDecimal("6.80") : new BigDecimal("2.20");
                return new HeaderBiddingOrchestrator.BidOffer(partnerId(), "cr_vip", bidPrice, "<div>VIP Ad</div>", "USD");
            }
        };

        HeaderBiddingOrchestrator.DemandPartnerAdapter secondDsp = new HeaderBiddingOrchestrator.DemandPartnerAdapter() {
            @Override
            public String partnerId() {
                return "DSP_Standard";
            }

            @Override
            public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
                return new HeaderBiddingOrchestrator.BidOffer(partnerId(), "cr_std", new BigDecimal("3.10"), "<div>Std Ad</div>", "USD");
            }
        };

        MediationService.MediationResult result = orchestrator.orchestrateUnifiedYieldPipeline(
                slot, "US", 1, 20, Set.of("whale_purchaser"),
                80L, 5, List.of(contextAwareDsp, secondDsp),
                null, List.of(), "<div>Backfill</div>",
                yieldManager, mediationService
        );

        assertNotNull(result);
        assertFalse(result.isBackfill());
        assertEquals(MediationService.AuctionTier.OPEN_HEADER_BIDDING, result.winningTier());
        assertEquals("DSP_Contextual_AI", result.winnerBidderId());
        assertEquals(0, new BigDecimal("6.80").compareTo(result.winningPrice()));

        // 验证成交结果已自动回流至 DynamicYieldManager
        DynamicYieldManager.MarketYieldStats stats = yieldManager.getStats("slot_native_feed");
        assertNotNull(stats);
        assertEquals(1L, stats.totalAuctions.get());
        assertEquals(1L, stats.filledAuctions.get());
    }
}
