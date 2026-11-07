package com.affiliate.platform.dsp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeepCtrCvrInferenceEngineTest {

    private DeepCtrCvrInferenceEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeepCtrCvrInferenceEngine();
    }

    @Test
    void testPredictionOutputBounds() {
        TrafficContext ctx = new TrafficContext(
                "finance.yahoo.com",
                2, // Mobile
                LocalDate.now(),
                14,
                LocalDate.now().getDayOfWeek(),
                "US",
                Set.of("investor", "crypto")
        );

        Campaign campaign = new Campaign(
                "camp_001",
                "fin_adv_99",
                "Crypto Investment App",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(30),
                BigDecimal.valueOf(1000.0),
                BigDecimal.valueOf(10.00),
                Set.of("finance.yahoo.com"),
                Set.of(2),
                Campaign.Status.ACTIVE,
                null
        );

        DeepCtrCvrInferenceEngine.PredictionResult pred = engine.predict(ctx, campaign, BigDecimal.valueOf(1.50));

        assertNotNull(pred);
        assertTrue(pred.pCtr() >= 0.0001 && pred.pCtr() <= 0.9500);
        assertTrue(pred.pCvr() >= 0.0001 && pred.pCvr() <= 0.9500);

        // 先验分布应处于合理工业区间 (CTR: 1% ~ 8%, CVR: 0.5% ~ 5%)
        assertTrue(pred.pCtr() > 0.005 && pred.pCtr() < 0.20);
        assertTrue(pred.pCvr() > 0.002 && pred.pCvr() < 0.15);

        BigDecimal ecpm = pred.calculateExpectedEcpm(BigDecimal.valueOf(20.00));
        assertNotNull(ecpm);
        assertTrue(ecpm.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testExtractFeatureIndices() {
        TrafficContext ctx = TrafficContext.of("google.com", 1, LocalDate.now());
        Campaign c = new Campaign(
                "c1", "adv1", "Test",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                BigDecimal.valueOf(500), BigDecimal.valueOf(5.0),
                Set.of(), Set.of(),
                Campaign.Status.ACTIVE, null
        );

        int[] indices = engine.extractFeatureIndices(ctx, c, BigDecimal.valueOf(2.0));
        assertEquals(6, indices.length);
        for (int idx : indices) {
            assertTrue(idx >= 0 && idx < DeepCtrCvrInferenceEngine.HASH_BUCKET_SIZE);
        }
    }

    @Test
    void testOnlineSgdUpdateImprovesPositiveFeedbackPrediction() {
        TrafficContext ctx = new TrafficContext(
                "techcrunch.com",
                1,
                LocalDate.now(),
                10,
                LocalDate.now().getDayOfWeek(),
                "US",
                Set.of("saas", "ai")
        );
        Campaign campaign = new Campaign(
                "camp_ai",
                "adv_ai_01",
                "Enterprise AI Cloud",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(30),
                BigDecimal.valueOf(2000.0),
                BigDecimal.valueOf(25.00),
                Set.of("techcrunch.com"),
                Set.of(1),
                Campaign.Status.ACTIVE,
                null
        );
        BigDecimal floor = BigDecimal.valueOf(2.00);

        DeepCtrCvrInferenceEngine.PredictionResult initialPred = engine.predict(ctx, campaign, floor);

        // 连续注入 5 次正向点击与转化样本反馈
        for (int i = 0; i < 5; i++) {
            engine.updateModel(ctx, campaign, floor, true, true);
        }

        DeepCtrCvrInferenceEngine.PredictionResult updatedPred = engine.predict(ctx, campaign, floor);
        assertEquals(5L, engine.getOnlineUpdateCount());
        assertTrue(updatedPred.pCtr() > initialPred.pCtr(), "Online positive click feedback should increase pCTR");
        assertTrue(updatedPred.pCvr() > initialPred.pCvr(), "Online positive conversion feedback should increase pCVR");
    }
}
