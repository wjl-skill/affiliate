package com.affiliate.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 商业级系统与业务核心 SLO 监控指标收集器 (SLO Metrics Collector)
 * <p>
 * 依托 Micrometer 统一注册核心服务级别目标 (SLO) 度量指标：
 * 1. RTB 竞价延迟 (SLO: P99 < 20ms) -> timer: rtb.bidding.latency
 * 2. 追踪网关重定向延迟 (SLO: P99 < 50ms) -> timer: click.redirect.latency
 * 3. S2S 回传处理量与状态分布 -> counter: s2s.postback.processed (tags: tenant, event, status)
 * 4. 复式记账账本单边账差错量 -> counter: billing.ledger.reconcile.discrepancy (tags: tenant)
 * 5. Outbox 投递连续失败死信量 -> counter: outbox.deadletter.total (tags: aggregate_type)
 * 6. AI 创意日预算超额熔断数 -> counter: ai.budget.exceeded.total (tags: tenant)
 */
@Component
public class SloMetricsCollector {

    public static final String METRIC_RTB_LATENCY = "rtb.bidding.latency";
    public static final String METRIC_CLICK_LATENCY = "click.redirect.latency";
    public static final String METRIC_S2S_POSTBACK = "s2s.postback.processed";
    public static final String METRIC_LEDGER_DISCREPANCY = "billing.ledger.reconcile.discrepancy";
    public static final String METRIC_OUTBOX_DEADLETTER = "outbox.deadletter.total";
    public static final String METRIC_AI_BUDGET_EXCEEDED = "ai.budget.exceeded.total";

    private final MeterRegistry registry;

    public SloMetricsCollector() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public SloMetricsCollector(@Autowired(required = false) MeterRegistry registry) {
        this.registry = registry != null ? registry : new SimpleMeterRegistry();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * 记录 RTB 竞价请求耗时 (毫秒)
     */
    public void recordRtbLatency(String tenant, boolean success, long durationMillis) {
        Timer.builder(METRIC_RTB_LATENCY)
                .description("RTB auction bidding latency (SLO P99 < 20ms)")
                .tag("tenant", safeTag(tenant))
                .tag("status", success ? "success" : "failure")
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(50))
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录点击追踪网关跳转处理耗时 (毫秒)
     */
    public void recordClickRedirectLatency(String tenant, long durationMillis) {
        Timer.builder(METRIC_CLICK_LATENCY)
                .description("Click redirect latency (SLO P99 < 50ms)")
                .tag("tenant", safeTag(tenant))
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(Duration.ofMillis(20), Duration.ofMillis(50), Duration.ofMillis(100))
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 S2S 回传事件与校验状态
     */
    public void recordS2sPostback(String tenant, String eventType, String status) {
        Counter.builder(METRIC_S2S_POSTBACK)
                .description("Total S2S postback callbacks processed")
                .tag("tenant", safeTag(tenant))
                .tag("event", safeTag(eventType))
                .tag("status", safeTag(status))
                .register(registry)
                .increment();
    }

    /**
     * 记录账本自动巡检发现的单边账或物化余额差错
     */
    public void recordReconcileDiscrepancy(String tenant, double amount) {
        Counter.builder(METRIC_LEDGER_DISCREPANCY)
                .description("Total immutable double-entry ledger reconcile discrepancies")
                .tag("tenant", safeTag(tenant))
                .register(registry)
                .increment();

        DistributionSummary.builder("billing.ledger.reconcile.discrepancy.amount")
                .description("Discrepancy absolute monetary value")
                .tag("tenant", safeTag(tenant))
                .register(registry)
                .record(Math.abs(amount));
    }

    /**
     * 记录 Transactional Outbox 隔离移入死信表的事件
     */
    public void recordOutboxDeadLetter(String aggregateType) {
        Counter.builder(METRIC_OUTBOX_DEADLETTER)
                .description("Total events moved to dead letter queue after max retry exhaustion")
                .tag("aggregate_type", safeTag(aggregateType))
                .register(registry)
                .increment();
    }

    /**
     * 记录租户 AI 创意日预算超额阻断事件
     */
    public void recordAiBudgetExceeded(String tenant) {
        Counter.builder(METRIC_AI_BUDGET_EXCEEDED)
                .description("Total AI generation requests rejected due to tenant daily budget cap")
                .tag("tenant", safeTag(tenant))
                .register(registry)
                .increment();
    }

    private String safeTag(String val) {
        return (val == null || val.isBlank()) ? "unknown" : val.trim();
    }
}
