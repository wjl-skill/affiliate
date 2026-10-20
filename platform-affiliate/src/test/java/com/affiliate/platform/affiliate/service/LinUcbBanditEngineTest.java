package com.affiliate.platform.affiliate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinUcbBanditEngineTest {

    private LinUcbBanditEngine banditEngine;

    @BeforeEach
    void setUp() {
        banditEngine = new LinUcbBanditEngine(0.5, 1.0);
    }

    @Test
    void testExtractFeatureVector() {
        LinUcbBanditEngine.BanditContext ctx = new LinUcbBanditEngine.BanditContext(
                "US", 2, 14, "aff_fb_01", BigDecimal.valueOf(3.50), true
        );
        double[] x = banditEngine.extractFeatureVector(ctx);

        assertEquals(LinUcbBanditEngine.FEATURE_DIM, x.length);
        assertEquals(1.0, x[0], 1e-6); // intercept
        assertEquals(0.5, x[1], 1e-6); // 2 / 4.0
        assertTrue(x[5] > 0.9); // sub1 present
        assertEquals(0.35, x[6], 1e-6); // 3.50 / 10.0
        assertEquals(1.0, x[7], 1e-6); // VIP
    }

    @Test
    void testColdStartSelection() {
        List<String> offers = List.of("offer_1", "offer_2", "offer_3");
        LinUcbBanditEngine.BanditContext ctx = LinUcbBanditEngine.BanditContext.of("US", 1, 10);

        String selected = banditEngine.selectBestOffer("t1", "link1", offers, ctx);
        assertNotNull(selected);
        assertTrue(offers.contains(selected));
    }

    @Test
    void testOnlineLearningConvergence() {
        String tenantId = "t1";
        String smartLinkId = "link1";
        String winningOffer = "offer_win";
        String losingOffer = "offer_loss";
        List<String> candidates = List.of(winningOffer, losingOffer);

        LinUcbBanditEngine.BanditContext ctx = LinUcbBanditEngine.BanditContext.of("US", 2, 12);

        // 模拟 winningOffer 连续产生高额收益，losingOffer 无收益
        for (int i = 0; i < 20; i++) {
            banditEngine.updateArm(tenantId, smartLinkId, winningOffer, ctx, 5.0);
            banditEngine.updateArm(tenantId, smartLinkId, losingOffer, ctx, 0.0);
        }

        // 训练后，模型应显著优先选择 winningOffer
        String bestOffer = banditEngine.selectBestOffer(tenantId, smartLinkId, candidates, ctx);
        assertEquals(winningOffer, bestOffer);
    }

    @Test
    void testRewardClampingAndStability() {
        String tenantId = "t1";
        String smartLinkId = "link1";
        String offer = "offer_extreme";
        LinUcbBanditEngine.BanditContext ctx = LinUcbBanditEngine.BanditContext.of("GB", 1, 8);

        // 极端异常大单收益 $1000 应被钳位至 10.0
        banditEngine.updateArm(tenantId, smartLinkId, offer, ctx, 1000.0);

        String best = banditEngine.selectBestOffer(tenantId, smartLinkId, List.of(offer), ctx);
        assertEquals(offer, best);
    }
}
