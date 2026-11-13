package com.affiliate.platform.reporting.olap;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.reporting.DerivedMetrics;
import com.affiliate.platform.reporting.PerformanceMetricsCalculator;
import com.affiliate.platform.reporting.olap.OlapReportingEngine.OlapAggregationRow;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClickHouse 多维 OLAP 实时事件流摄入订阅器 (Real-Time OLAP Event Stream Subscriber)
 * <p>
 * 监听 {@code impression.recorded.v1}、{@code click.recorded.v1}、{@code auction.win.v1}、
 * {@code conversion.approved.v1} 领域事件，实时增量更新 OLAP 内存聚合物化视图并同步至 {@link ClickHouseOlapClient}。
 */
@Component
public class OlapEventStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(OlapEventStreamSubscriber.class);

    static final class MutableCubeBucket {
        final String dimensionValue;
        long impressions = 0;
        long clicks = 0;
        long conversions = 0;
        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;

        MutableCubeBucket(String dimensionValue) {
            this.dimensionValue = dimensionValue;
        }
    }

    private final ClickHouseOlapClient clickHouseClient;
    private final EventPublisher eventPublisher;
    private final ConcurrentMap<String, ConcurrentMap<String, MutableCubeBucket>> tenantCampaignCubes = new ConcurrentHashMap<>();
    private final AtomicLong totalIngestedEvents = new AtomicLong(0);

    @Autowired
    public OlapEventStreamSubscriber(
            ClickHouseOlapClient clickHouseClient,
            @Autowired(required = false) EventPublisher eventPublisher
    ) {
        this.clickHouseClient = clickHouseClient;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void initSubscription() {
        if (eventPublisher != null) {
            eventPublisher.subscribe(this::onDomainEvent);
        }
    }

    public void onDomainEvent(DomainEvent<?> event) {
        if (event == null || event.eventType() == null) {
            return;
        }
        String eventType = event.eventType();
        if (!("impression.recorded.v1".equals(eventType)
                || "click.recorded.v1".equals(eventType)
                || "auction.win.v1".equals(eventType)
                || "conversion.approved.v1".equals(eventType))) {
            return;
        }

        String tenantId = event.tenantId() != null ? event.tenantId() : "public";
        Object payload = event.payload();
        String campaignId = extractString(payload, "campaignId",
                extractString(payload, "offerId", "campaign_default"));
        double price = extractDouble(payload, "price", 0.0);
        double payout = extractDouble(payload, "payout", 0.0);
        double rev = extractDouble(payload, "revenue", 0.0);

        ConcurrentMap<String, MutableCubeBucket> cube =
                tenantCampaignCubes.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        MutableCubeBucket bucket = cube.computeIfAbsent(campaignId, MutableCubeBucket::new);

        synchronized (bucket) {
            switch (eventType) {
                case "impression.recorded.v1" -> bucket.impressions++;
                case "click.recorded.v1" -> bucket.clicks++;
                case "auction.win.v1" -> {
                    if (price > 0.0) {
                        BigDecimal winCost = BigDecimal.valueOf(price)
                                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
                        bucket.spend = bucket.spend.add(winCost);
                    }
                }
                case "conversion.approved.v1" -> {
                    bucket.conversions++;
                    if (payout > 0.0) {
                        bucket.spend = bucket.spend.add(BigDecimal.valueOf(payout));
                    }
                    if (rev > 0.0) {
                        bucket.revenue = bucket.revenue.add(BigDecimal.valueOf(rev));
                    }
                }
            }
        }

        syncMaterializedViewToClient(tenantId, cube);
        totalIngestedEvents.incrementAndGet();
    }

    private void syncMaterializedViewToClient(String tenantId, ConcurrentMap<String, MutableCubeBucket> cube) {
        if (clickHouseClient == null) {
            return;
        }
        List<OlapAggregationRow> rows = new ArrayList<>(cube.size());
        for (MutableCubeBucket b : cube.values()) {
            synchronized (b) {
                DerivedMetrics derived = PerformanceMetricsCalculator.calculate(
                        b.impressions, b.clicks, b.conversions, b.spend, b.revenue
                );
                BigDecimal profit = b.revenue.subtract(b.spend).setScale(4, RoundingMode.HALF_UP);
                rows.add(new OlapAggregationRow(
                        b.dimensionValue,
                        b.clicks,
                        b.conversions,
                        b.spend,
                        b.revenue,
                        profit,
                        derived
                ));
            }
        }
        clickHouseClient.registerMockResult(tenantId, "campaignId", rows);
    }

    public long getTotalIngestedEvents() {
        return totalIngestedEvents.get();
    }

    private static String extractString(Object obj, String field, String fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Map<?, ?> map) {
            Object val = map.get(field);
            return val != null ? String.valueOf(val) : fallback;
        }
        try {
            Method m = obj.getClass().getMethod(field);
            Object val = m.invoke(obj);
            return val != null ? String.valueOf(val) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double extractDouble(Object obj, String field, double fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Map<?, ?> map) {
            Object val = map.get(field);
            if (val instanceof Number num) return num.doubleValue();
            return fallback;
        }
        try {
            Method m = obj.getClass().getMethod(field);
            Object val = m.invoke(obj);
            if (val instanceof Number num) return num.doubleValue();
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
