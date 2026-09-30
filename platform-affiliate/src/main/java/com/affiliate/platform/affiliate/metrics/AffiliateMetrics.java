package com.affiliate.platform.affiliate.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网盟广告平台生产级全链路度量指标集 (Affiliate Core Micrometer Metrics)
 * <p>
 * 提供点击吞吐与削峰指标、S2S 转化归因与排他锁争用指标、反作弊拦截与 TDS 智能分流指标。
 * 兼容未配置 MeterRegistry 时的优雅降级（基于 SimpleMeterRegistry）。
 */
@Component
public class AffiliateMetrics {

    private final MeterRegistry registry;

    // 点击相关
    private final Counter clicksSuccess;
    private final Counter clicksDropped;
    private final Timer clickLatency;

    // 转化与风控相关
    private final Counter conversionsApproved;
    private final Counter conversionsFraud;
    private final Counter conversionsRejected;
    private final Counter postbackLockContention;

    // TDS 路由相关
    private final Counter tdsRouteRoundRobin;
    private final Counter tdsRouteHighestEpc;
    private final Counter tdsRouteFallback;
    private final AtomicInteger degradedOfferCount = new AtomicInteger(0);

    public AffiliateMetrics() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public AffiliateMetrics(@Autowired(required = false) MeterRegistry registry) {
        this.registry = registry != null ? registry : new SimpleMeterRegistry();

        this.clicksSuccess = Counter.builder("affiliate.clicks.total")
                .tag("status", "success")
                .description("Total valid clicks successfully accepted")
                .register(this.registry);

        this.clicksDropped = Counter.builder("affiliate.clicks.total")
                .tag("status", "dropped")
                .description("Clicks dropped due to queue congestion or backpressure")
                .register(this.registry);

        this.clickLatency = Timer.builder("affiliate.click.latency")
                .description("Click tracking latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(this.registry);

        this.conversionsApproved = Counter.builder("affiliate.conversions.total")
                .tag("status", "approved")
                .description("Total approved conversions recorded")
                .register(this.registry);

        this.conversionsFraud = Counter.builder("affiliate.conversions.total")
                .tag("status", "fraud_suspected")
                .description("Conversions intercepted by anti-fraud engine")
                .register(this.registry);

        this.conversionsRejected = Counter.builder("affiliate.conversions.total")
                .tag("status", "rejected")
                .description("Conversions rejected by duplicate txid or business caps")
                .register(this.registry);

        this.postbackLockContention = Counter.builder("affiliate.postback.lock.contention.total")
                .description("Concurrent postback transaction lock acquisition contentions")
                .register(this.registry);

        this.tdsRouteRoundRobin = Counter.builder("affiliate.tds.routes.total")
                .tag("strategy", "round_robin")
                .description("SmartLink TDS routes dispatched via Round-Robin")
                .register(this.registry);

        this.tdsRouteHighestEpc = Counter.builder("affiliate.tds.routes.total")
                .tag("strategy", "highest_epc")
                .description("SmartLink TDS routes dispatched via Highest-EPC")
                .register(this.registry);

        this.tdsRouteFallback = Counter.builder("affiliate.tds.fallback.total")
                .description("SmartLink TDS routes downgraded to fallback offer")
                .register(this.registry);

        this.registry.gauge("affiliate.tds.offers.degraded", degradedOfferCount);
    }

    public void recordClickSuccess(long durationNanos) {
        clicksSuccess.increment();
        clickLatency.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordClickDropped() {
        clicksDropped.increment();
    }

    public void recordConversionApproved() {
        conversionsApproved.increment();
    }

    public void recordConversionFraud() {
        conversionsFraud.increment();
    }

    public void recordConversionRejected() {
        conversionsRejected.increment();
    }

    public void recordPostbackLockContention() {
        postbackLockContention.increment();
    }

    public void recordTdsRouteRoundRobin() {
        tdsRouteRoundRobin.increment();
    }

    public void recordTdsRouteHighestEpc() {
        tdsRouteHighestEpc.increment();
    }

    public void recordTdsRouteFallback() {
        tdsRouteFallback.increment();
    }

    public void updateDegradedOfferCount(int count) {
        degradedOfferCount.set(count);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
