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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClickHouse 多维 OLAP 微批异步流摄入订阅器 (Micro-Batch Async OLAP Event Stream Subscriber)
 * <p>
 * 监听 {@code impression.recorded.v1}、{@code click.recorded.v1}、{@code auction.win.v1}、
 * {@code conversion.approved.v1} 领域事件，采用有界队列缓冲 + 微批定量/定时 Flush 机制：
 * <ul>
 *     <li>热路径零锁入队，消除逐条事件重建聚合视图的锁竞争；</li>
 *     <li>按 {@code campaignId}、{@code offerId}、{@code country}、{@code sub1} 多维同步更新内存降级物化视图；</li>
 *     <li>当 ClickHouse 可用时通过 {@link ClickHouseOlapClient#insertFactEventsBatch} 批量写入数仓。</li>
 * </ul>
 */
@Component
public class OlapEventStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(OlapEventStreamSubscriber.class);
    private static final int MAX_PENDING_QUEUE_SIZE = 10_000;
    private static final int DEFAULT_BATCH_SIZE = 200;

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

    // tenantId -> dimensionName -> dimensionValue -> bucket
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, MutableCubeBucket>>> tenantDimensionCubes =
            new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<DomainEvent<?>> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingQueueSize = new AtomicInteger(0);

    private final AtomicLong totalIngestedEvents = new AtomicLong(0);
    private final AtomicLong totalFlushedBatches = new AtomicLong(0);
    private final AtomicLong droppedEvents = new AtomicLong(0);

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

        if (pendingQueueSize.get() >= MAX_PENDING_QUEUE_SIZE) {
            droppedEvents.incrementAndGet();
            return;
        }
        pendingQueue.offer(event);
        int size = pendingQueueSize.incrementAndGet();
        totalIngestedEvents.incrementAndGet();

        if (size >= DEFAULT_BATCH_SIZE) {
            flushBatch();
        }
    }

    /**
     * 定时或手动触发微批消费刷写（每 500ms 执行一次）
     */
    @Scheduled(fixedDelay = 500)
    public synchronized int flushBatch() {
        if (pendingQueue.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> clickHouseRows = new ArrayList<>();
        Set<String> dirtyTenantDimensions = new HashSet<>();
        int processed = 0;

        while (processed < DEFAULT_BATCH_SIZE) {
            DomainEvent<?> event = pendingQueue.poll();
            if (event == null) {
                break;
            }
            pendingQueueSize.decrementAndGet();
            processed++;

            String eventType = event.eventType();
            String tenantId = event.tenantId() != null ? event.tenantId() : "public";
            Object payload = event.payload();

            String campaignId = extractString(payload, "campaignId",
                    extractString(payload, "offerId", "campaign_default"));
            String offerId = extractString(payload, "offerId", campaignId);
            String country = extractString(payload, "country", "UNKNOWN");
            String sub1 = extractString(payload, "sub1", "direct");

            double price = extractDouble(payload, "price", 0.0);
            double payout = extractDouble(payload, "payout", 0.0);
            double rev = extractDouble(payload, "revenue", 0.0);

            accumulateDimension(tenantId, "campaignId", campaignId, eventType, price, payout, rev);
            accumulateDimension(tenantId, "offerId", offerId, eventType, price, payout, rev);
            accumulateDimension(tenantId, "country", country, eventType, price, payout, rev);
            accumulateDimension(tenantId, "sub1", sub1, eventType, price, payout, rev);

            dirtyTenantDimensions.add(tenantId + "|campaignId");
            dirtyTenantDimensions.add(tenantId + "|offerId");
            dirtyTenantDimensions.add(tenantId + "|country");
            dirtyTenantDimensions.add(tenantId + "|sub1");

            Map<String, Object> chRow = new LinkedHashMap<>();
            chRow.put("tenant_id", tenantId);
            chRow.put("report_date", LocalDate.ofInstant(
                    event.occurredAt() != null ? event.occurredAt() : Instant.now(), ZoneOffset.UTC).toString());
            chRow.put("offer_id", offerId);
            chRow.put("country", country);
            chRow.put("sub1", sub1);
            chRow.put("clicks", "click.recorded.v1".equals(eventType) ? 1 : 0);
            chRow.put("conversions", "conversion.approved.v1".equals(eventType) ? 1 : 0);
            chRow.put("payout", "auction.win.v1".equals(eventType) && price > 0.0
                    ? BigDecimal.valueOf(price).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(payout));
            chRow.put("revenue", BigDecimal.valueOf(rev));
            clickHouseRows.add(chRow);
        }

        if (processed > 0) {
            for (String td : dirtyTenantDimensions) {
                int sep = td.indexOf('|');
                syncMaterializedViewToClient(td.substring(0, sep), td.substring(sep + 1));
            }
            if (clickHouseClient != null) {
                clickHouseClient.insertFactEventsBatch(clickHouseRows);
            }
            totalFlushedBatches.incrementAndGet();
            log.debug("[OlapMicroBatch] Flushed {} events across {} tenant-dimension views",
                    processed, dirtyTenantDimensions.size());
        }
        return processed;
    }

    private void accumulateDimension(
            String tenantId,
            String dimensionName,
            String dimensionValue,
            String eventType,
            double price,
            double payout,
            double rev
    ) {
        ConcurrentMap<String, MutableCubeBucket> cube = tenantDimensionCubes
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(dimensionName, k -> new ConcurrentHashMap<>());
        MutableCubeBucket bucket = cube.computeIfAbsent(dimensionValue, MutableCubeBucket::new);

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

    private void syncMaterializedViewToClient(String tenantId, String dimensionName) {
        if (clickHouseClient == null) {
            return;
        }
        ConcurrentMap<String, ConcurrentMap<String, MutableCubeBucket>> dims = tenantDimensionCubes.get(tenantId);
        if (dims == null) {
            return;
        }
        ConcurrentMap<String, MutableCubeBucket> cube = dims.get(dimensionName);
        if (cube == null) {
            return;
        }
        List<OlapAggregationRow> rows = new ArrayList<>(cube.size());
        for (MutableCubeBucket b : cube.values()) {
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
        clickHouseClient.registerMockResult(tenantId, dimensionName, rows);
    }

    public long getTotalIngestedEvents() {
        return totalIngestedEvents.get();
    }

    public long getTotalFlushedBatches() {
        return totalFlushedBatches.get();
    }

    public int getPendingQueueSize() {
        return pendingQueueSize.get();
    }

    public long getDroppedEvents() {
        return droppedEvents.get();
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
