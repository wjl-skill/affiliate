package com.affiliate.platform.rtb;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * OpenRTB 实时竞价系统生产级度量指标集 (RTB Metrics)
 * <p>
 * 精准捕获竞价 P99 纳秒级延迟、超时截断保护、频控拦截与预算耗尽等关键运行时特征。
 */
@Component
public class RtbMetrics {

    private final MeterRegistry registry;

    private final Counter bidsSubmitted;
    private final Counter bidsNoBid;
    private final Counter bidsTimeoutCutoff;
    private final Timer bidLatency;

    private final Counter freqCapBlocked;
    private final Counter budgetExhausted;

    public RtbMetrics() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public RtbMetrics(@Autowired(required = false) MeterRegistry registry) {
        this.registry = registry != null ? registry : new SimpleMeterRegistry();

        this.bidsSubmitted = Counter.builder("rtb.bid.requests.total")
                .tag("decision", "bid")
                .description("Total RTB auctions with winning bids submitted")
                .register(this.registry);

        this.bidsNoBid = Counter.builder("rtb.bid.requests.total")
                .tag("decision", "nobid")
                .description("Total RTB auctions resulting in no bid")
                .register(this.registry);

        this.bidsTimeoutCutoff = Counter.builder("rtb.bid.requests.total")
                .tag("decision", "timeout_cutoff")
                .description("RTB auctions forcibly truncated by 15ms deadline safeguard")
                .register(this.registry);

        this.bidLatency = Timer.builder("rtb.bid.latency")
                .description("RTB auction processing latency")
                .publishPercentiles(0.5, 0.90, 0.99)
                .register(this.registry);

        this.freqCapBlocked = Counter.builder("rtb.freqcap.blocked.total")
                .description("Total RTB candidate bids filtered out by user frequency capping")
                .register(this.registry);

        this.budgetExhausted = Counter.builder("rtb.budget.exhausted.total")
                .description("Total RTB candidate bids rejected due to campaign budget depletion")
                .register(this.registry);
    }

    public void recordBidSubmitted(long latencyNanos) {
        bidsSubmitted.increment();
        bidLatency.record(latencyNanos, TimeUnit.NANOSECONDS);
    }

    public void recordNoBid(long latencyNanos) {
        bidsNoBid.increment();
        bidLatency.record(latencyNanos, TimeUnit.NANOSECONDS);
    }

    public void recordTimeoutCutoff() {
        bidsTimeoutCutoff.increment();
    }

    public void recordFreqCapBlocked() {
        freqCapBlocked.increment();
    }

    public void recordBudgetExhausted() {
        budgetExhausted.increment();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
