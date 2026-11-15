package com.affiliate.platform.dsp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3 DSP 升级验证：64K 域隔离 FM 深度预估、模型快照持久化与 PID 日内匀速预算控制器测试")
class Phase3DspFmAndPacingUpgradeTest {

    @Test
    @DisplayName("1. 验证 65,536 桶特征域物理隔离 (Namespace Isolation) 与扩展交叉特征零碰撞")
    void testNamespaceIsolatedFeatureBucketsAndSnapshotPersistence() {
        DeepCtrCvrInferenceEngine engine = new DeepCtrCvrInferenceEngine();
        assertEquals(65536, DeepCtrCvrInferenceEngine.HASH_BUCKET_SIZE);

        TrafficContext ctx = new TrafficContext(
                "bloomberg.com", 2, LocalDate.now(), 20,
                LocalDate.now().getDayOfWeek(), "US", Set.of("whale_purchaser", "fintech")
        );
        Campaign campaign = new Campaign(
                "camp_quant", "adv_wallstreet", "Quant Trading Pro",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30),
                new BigDecimal("5000.00"), new BigDecimal("18.00"),
                Set.of("bloomberg.com"), Set.of(2),
                Campaign.Status.ACTIVE, Instant.now()
        );
        BigDecimal floor = new BigDecimal("2.50");

        int[] extIndices = engine.extractExtendedFeatureIndices(ctx, campaign, floor);
        assertEquals(8, extIndices.length);

        // 验证 8 个特征严格落入各自独立的 8192 桶子空间 [f*8192, (f+1)*8192)，彻底消除跨特征域碰撞
        Set<Integer> uniqueIndices = new HashSet<>();
        for (int f = 0; f < 8; f++) {
            int idx = extIndices[f];
            assertTrue(idx >= f * 8192 && idx < (f + 1) * 8192,
                    "Field " + f + " index " + idx + " must stay within namespace [" + (f * 8192) + ", " + ((f + 1) * 8192) + ")");
            uniqueIndices.add(idx);
        }
        assertEquals(8, uniqueIndices.size(), "不同特征域之间的哈希桶索引绝不发生碰撞");

        // 在线学习更新 10 次正样本后导出模型快照
        for (int i = 0; i < 10; i++) {
            engine.updateModel(ctx, campaign, floor, true, true, 0.08);
        }
        DeepCtrCvrInferenceEngine.PredictionResult learnedPred = engine.predict(ctx, campaign, floor);
        DeepCtrCvrInferenceEngine.ModelSnapshot snapshot = engine.exportSnapshot();
        assertEquals(10L, snapshot.updateCount());

        // 创建全新冷启动引擎并导入快照，验证预估输出 100% 精确一致
        DeepCtrCvrInferenceEngine restoredEngine = new DeepCtrCvrInferenceEngine();
        restoredEngine.importSnapshot(snapshot);
        DeepCtrCvrInferenceEngine.PredictionResult restoredPred = restoredEngine.predict(ctx, campaign, floor);

        assertEquals(10L, restoredEngine.getOnlineUpdateCount());
        assertEquals(learnedPred.pCtr(), restoredPred.pCtr(), 1e-12);
        assertEquals(learnedPred.pCvr(), restoredPred.pCvr(), 1e-12);
    }

    @Test
    @DisplayName("2. 验证 PID 日内预算平滑消耗控制器：超支压价节流 vs 欠量提价追赶")
    void testPidBudgetPacingControllerOverspendAndUnderspend() {
        BudgetPacingController pacingController = new BudgetPacingController();
        BigDecimal dailyBudget = new BigDecimal("1000.00");

        // 场景 A: 早间日内时间仅过去 20% (0.20)，但预算已消耗 $550 (55%) -> 严重超前消耗
        BudgetPacingController.PacingDecision overspendDecision = pacingController.evaluatePacing(
                "camp_overspend", dailyBudget, new BigDecimal("550.00"), 0.20, false
        );
        assertFalse(overspendDecision.budgetExhausted());
        assertTrue(overspendDecision.pacingError() < -0.30);
        assertTrue(overspendDecision.bidPacingMultiplier().compareTo(BigDecimal.ONE) < 0,
                "消耗超前时 PID 控制器必须下调出价乘数 (< 1.0)");
        assertTrue(overspendDecision.passProbability() < 0.50,
                "严重超前消耗时应触发概率性节流降频 (< 50% 放行率)");

        // 场景 B: 下午日内时间已过去 75% (0.75)，但预算仅消耗 $250 (25%) -> 消耗滞后欠量
        BudgetPacingController.PacingDecision underspendDecision = pacingController.evaluatePacing(
                "camp_underspend", dailyBudget, new BigDecimal("250.00"), 0.75, false
        );
        assertFalse(underspendDecision.budgetExhausted());
        assertTrue(underspendDecision.pacingError() > 0.40);
        assertTrue(underspendDecision.bidPacingMultiplier().compareTo(BigDecimal.ONE) > 0,
                "消耗滞后时 PID 控制器应适度上调出价乘数 (> 1.0) 以追赶交付进度");
        assertEquals(1.0, underspendDecision.passProbability(), 1e-6,
                "消耗滞后时应保持 100% 参竞放行率");

        // 场景 C: 预算 100% 耗尽 -> 立即触发停投保护
        BudgetPacingController.PacingDecision exhaustedDecision = pacingController.evaluatePacing(
                "camp_overspend", dailyBudget, new BigDecimal("1000.00"), 0.85, true
        );
        assertTrue(exhaustedDecision.budgetExhausted());
        assertEquals(0.0, exhaustedDecision.passProbability(), 1e-6);
    }
}
