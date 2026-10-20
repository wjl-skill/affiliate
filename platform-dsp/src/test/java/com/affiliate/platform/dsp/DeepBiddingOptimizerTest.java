package com.affiliate.platform.dsp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeepBiddingOptimizerTest {

    private DeepBiddingOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new DeepBiddingOptimizer();
    }

    @Test
    void testOptimizeBidWithinNormalRange() {
        TrafficContext ctx = TrafficContext.of("techcrunch.com", 1, LocalDate.now());
        Campaign c = new Campaign(
                "c100", "adv100", "Tech Campaign",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                BigDecimal.valueOf(2000), BigDecimal.valueOf(10.0),
                Set.of(), Set.of(),
                Campaign.Status.ACTIVE, null
        );

        BigDecimal targetCpa = BigDecimal.valueOf(30.00);
        BigDecimal floorPrice = BigDecimal.valueOf(0.80);

        DeepBiddingOptimizer.DeepBiddingDecision decision = optimizer.optimizeBid(
                ctx, c, targetCpa, floorPrice, "slot_top:US:desktop"
        );

        assertNotNull(decision);
        if (decision.reason() != DeepBiddingOptimizer.DecisionReason.BELOW_FLOOR_ABORT) {
            assertTrue(decision.bidAmount().compareTo(floorPrice) >= 0);
            assertTrue(decision.bidAmount().compareTo(DeepBiddingOptimizer.DEFAULT_MAX_BID_GUARD) <= 0);
            assertTrue(decision.shadingRatio() >= 0.50 && decision.shadingRatio() <= 1.0);
        }
    }

    @Test
    void testOptimizeBidBelowFloorAbort() {
        TrafficContext ctx = TrafficContext.of("news.com", 2, LocalDate.now());
        Campaign c = new Campaign(
                "c200", "adv200", "Low CPA Campaign",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5),
                BigDecimal.valueOf(100), BigDecimal.valueOf(2.0),
                Set.of(), Set.of(),
                Campaign.Status.ACTIVE, null
        );

        // 设置极其低的目标 CPA $0.01 与高昂底价 $50.00，必然触发低于底价放弃竞价
        BigDecimal targetCpa = BigDecimal.valueOf(0.01);
        BigDecimal floorPrice = BigDecimal.valueOf(50.00);

        DeepBiddingOptimizer.DeepBiddingDecision decision = optimizer.optimizeBid(
                ctx, c, targetCpa, floorPrice, "slot_expensive:DE:mobile"
        );

        assertNotNull(decision);
        assertEquals(DeepBiddingOptimizer.DecisionReason.BELOW_FLOOR_ABORT, decision.reason());
        assertEquals(BigDecimal.ZERO, decision.bidAmount());
    }
}
