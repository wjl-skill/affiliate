package com.affiliate.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("商业级系统与业务 SLO 指标收集器测试 (SloMetricsCollectorTest)")
class SloMetricsCollectorTest {

    private SimpleMeterRegistry registry;
    private SloMetricsCollector collector;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new SloMetricsCollector(registry);
    }

    @Test
    @DisplayName("RTB 竞价耗时与成功/失败状态正确记录到 Timer")
    void testRecordRtbLatency() {
        collector.recordRtbLatency("tenant_dsp_01", true, 12);
        collector.recordRtbLatency("tenant_dsp_01", true, 18);
        collector.recordRtbLatency("tenant_dsp_01", false, 45);

        Timer successTimer = registry.find(SloMetricsCollector.METRIC_RTB_LATENCY)
                .tag("tenant", "tenant_dsp_01")
                .tag("status", "success")
                .timer();

        assertNotNull(successTimer);
        assertEquals(2, successTimer.count());
        assertEquals(30.0, successTimer.totalTime(TimeUnit.MILLISECONDS), 0.001);

        Timer failTimer = registry.find(SloMetricsCollector.METRIC_RTB_LATENCY)
                .tag("tenant", "tenant_dsp_01")
                .tag("status", "failure")
                .timer();

        assertNotNull(failTimer);
        assertEquals(1, failTimer.count());
        assertEquals(45.0, failTimer.totalTime(TimeUnit.MILLISECONDS), 0.001);
    }

    @Test
    @DisplayName("点击网关重定向耗时正确打点")
    void testRecordClickRedirectLatency() {
        collector.recordClickRedirectLatency("tenant_tracker_01", 28);
        collector.recordClickRedirectLatency("tenant_tracker_01", 35);

        Timer timer = registry.find(SloMetricsCollector.METRIC_CLICK_LATENCY)
                .tag("tenant", "tenant_tracker_01")
                .timer();

        assertNotNull(timer);
        assertEquals(2, timer.count());
        assertEquals(63.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.001);
    }

    @Test
    @DisplayName("S2S 回传处理量与各种校验状态计数统计")
    void testRecordS2sPostback() {
        collector.recordS2sPostback("tenant_aff_01", "CONVERSION", "SUCCESS");
        collector.recordS2sPostback("tenant_aff_01", "CONVERSION", "SUCCESS");
        collector.recordS2sPostback("tenant_aff_01", "CONVERSION", "REPLAY_ATTACK");

        Counter successCounter = registry.find(SloMetricsCollector.METRIC_S2S_POSTBACK)
                .tag("tenant", "tenant_aff_01")
                .tag("event", "CONVERSION")
                .tag("status", "SUCCESS")
                .counter();

        assertNotNull(successCounter);
        assertEquals(2.0, successCounter.count());

        Counter replayCounter = registry.find(SloMetricsCollector.METRIC_S2S_POSTBACK)
                .tag("tenant", "tenant_aff_01")
                .tag("event", "CONVERSION")
                .tag("status", "REPLAY_ATTACK")
                .counter();

        assertNotNull(replayCounter);
        assertEquals(1.0, replayCounter.count());
    }

    @Test
    @DisplayName("复式记账差错告警与差额统计分布正确记录")
    void testRecordReconcileDiscrepancy() {
        collector.recordReconcileDiscrepancy("tenant_billing_01", 150.50);

        Counter discrepancyCounter = registry.find(SloMetricsCollector.METRIC_LEDGER_DISCREPANCY)
                .tag("tenant", "tenant_billing_01")
                .counter();

        assertNotNull(discrepancyCounter);
        assertEquals(1.0, discrepancyCounter.count());

        DistributionSummary summary = registry.find("billing.ledger.reconcile.discrepancy.amount")
                .tag("tenant", "tenant_billing_01")
                .summary();

        assertNotNull(summary);
        assertEquals(1, summary.count());
        assertEquals(150.50, summary.totalAmount(), 0.001);
    }

    @Test
    @DisplayName("Outbox 死信归档与 AI 预算熔断计数统计")
    void testRecordOutboxDeadLetterAndAiBudget() {
        collector.recordOutboxDeadLetter("CAMPAIGN_SPEND");
        collector.recordOutboxDeadLetter("CAMPAIGN_SPEND");
        collector.recordAiBudgetExceeded("tenant_ai_01");

        Counter outboxCounter = registry.find(SloMetricsCollector.METRIC_OUTBOX_DEADLETTER)
                .tag("aggregate_type", "CAMPAIGN_SPEND")
                .counter();

        assertNotNull(outboxCounter);
        assertEquals(2.0, outboxCounter.count());

        Counter aiCounter = registry.find(SloMetricsCollector.METRIC_AI_BUDGET_EXCEEDED)
                .tag("tenant", "tenant_ai_01")
                .counter();

        assertNotNull(aiCounter);
        assertEquals(1.0, aiCounter.count());
    }
}
