package com.affiliate.platform.dsp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DynamicBidShadingEngineTest {

    @Test
    @DisplayName("冷启动默认打折与底价保护测试")
    void testColdStartAndFloorProtection() {
        DynamicBidShadingEngine engine = new DynamicBidShadingEngine();

        // 场景 1: 估值 $10.00，媒体底价 $2.00，无画像样本 -> 默认 0.85 折扣
        DynamicBidShadingEngine.ShadingResult res1 = engine.calculateShadedBid(
                new BigDecimal("10.00"), new BigDecimal("2.00"), "slot-1:US:mobile"
        );
        assertEquals(0, new BigDecimal("8.5000").compareTo(res1.shadedBid()));
        assertEquals(0.85, res1.shadingRatio(), 0.001);

        // 场景 2: 估值 $3.00，但底价高达 $2.80 -> 折扣后不应低于底价 $2.80
        DynamicBidShadingEngine.ShadingResult res2 = engine.calculateShadedBid(
                new BigDecimal("3.00"), new BigDecimal("2.80"), "slot-1:US:mobile"
        );
        assertEquals(0, new BigDecimal("2.8000").compareTo(res2.shadedBid()));

        // 场景 3: 估值低于底价
        DynamicBidShadingEngine.ShadingResult res3 = engine.calculateShadedBid(
                new BigDecimal("1.50"), new BigDecimal("2.00"), "slot-1:US:mobile"
        );
        assertEquals(0, new BigDecimal("1.5000").compareTo(res3.shadedBid()));
    }

    @Test
    @DisplayName("连续胜率曲线最优化剩余价值求解与在线自适应学习测试")
    void testLogisticOptimizationAndFeedbackLoop() {
        DynamicBidShadingEngine engine = new DynamicBidShadingEngine();
        String segment = "slot-hb:US:desktop";

        // 模拟 15 次竞胜，市场出清价大约在 $3.00~$3.50 左右
        for (int i = 0; i < 15; i++) {
            engine.recordWin(segment, 3.80, 3.20);
        }

        // 评估估值为 $6.00 的出价决策
        DynamicBidShadingEngine.ShadingResult decision = engine.calculateShadedBid(
                new BigDecimal("6.00"), new BigDecimal("1.00"), segment
        );

        // 算法应当自适应寻找使 (6.00 - b) * P(Win|b) 最大化的最优出价
        assertTrue(decision.shadedBid().compareTo(new BigDecimal("6.00")) < 0, "出价应被有效 Shading");
        assertTrue(decision.shadedBid().compareTo(new BigDecimal("1.00")) >= 0, "出价不低于底价");
        assertTrue(decision.shadingRatio() >= 0.50 && decision.shadingRatio() <= 0.98);

        // 连续落败后，算法感知市场水位上涨，提高出价
        for (int i = 0; i < 10; i++) {
            engine.recordLoss(segment, 3.50);
        }

        DynamicBidShadingEngine.ShadingResult afterLossDecision = engine.calculateShadedBid(
                new BigDecimal("6.00"), new BigDecimal("1.00"), segment
        );
        assertTrue(afterLossDecision.shadedBid().compareTo(decision.shadedBid()) >= 0,
                "市场竞争加剧落败后，自适应出价应上调以争取更高胜率");
    }

    @Test
    @DisplayName("阻尼加权出价调优矩阵测试（避免组合爆炸与全局钳位）")
    void testBidModifierDampingAndClamping() {
        BidModifierEngine engine = new BidModifierEngine();
        BigDecimal baseBid = new BigDecimal("2.0000");

        // 极端倍率组合：iOS (1.35x), US (1.50x), 晚间高峰 (1.25x), 质量极优 (1.50x)
        // 线性纯乘积: 1.35 * 1.50 * 1.25 * 1.50 = 3.796875 -> baseBid * 3.796875 = 7.5938
        BigDecimal linearBid = engine.calculateAdjustedBidLinear(
                baseBid, 2, "US", 20, new BigDecimal("1.50"), new BigDecimal("20.00")
        );
        assertEquals(0, new BigDecimal("7.5938").compareTo(linearBid));

        // 启用 0.70 阻尼
        BigDecimal dampedBid = engine.calculateAdjustedBidDamped(
                baseBid, 2, "US", 20, new BigDecimal("1.50"), new BigDecimal("20.00"),
                0.70, 0.30, 2.50
        );

        // 3.796875 ^ 0.70 = 2.535 -> 钳位至最大倍率 2.50 -> 2.00 * 2.50 = 5.0000
        assertEquals(0, new BigDecimal("5.0000").compareTo(dampedBid));
        assertTrue(dampedBid.compareTo(linearBid) < 0, "阻尼出价应有效抑制极端溢价");
    }
}
