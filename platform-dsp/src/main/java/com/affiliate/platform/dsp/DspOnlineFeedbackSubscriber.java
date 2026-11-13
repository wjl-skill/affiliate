package com.affiliate.platform.dsp;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DSP 深度模型在线学习与 Bid Shading 实时事件反馈订阅器 (DSP Online SGD & Bid Shading Feedback Subscriber)
 * <p>
 * 监听 {@code auction.win.v1}、{@code impression.recorded.v1}、{@code click.recorded.v1}、{@code conversion.approved.v1}，
 * 实时驱动：
 * <ul>
 *     <li>{@link DynamicBidShadingEngine#recordWin(String, double, double)} 更新一价市场竞争水位 $b_0$；</li>
 *     <li>{@link DeepCtrCvrInferenceEngine#updateModel(TrafficContext, Campaign, BigDecimal, boolean, boolean)} 在线 SGD 更新 FM 隐向量权重。</li>
 * </ul>
 */
@Component
public class DspOnlineFeedbackSubscriber {

    private final DeepCtrCvrInferenceEngine inferenceEngine;
    private final DynamicBidShadingEngine bidShadingEngine;
    private final EventPublisher eventPublisher;
    private final AtomicLong feedbackCount = new AtomicLong(0);

    @Autowired
    public DspOnlineFeedbackSubscriber(
            DeepCtrCvrInferenceEngine inferenceEngine,
            DynamicBidShadingEngine bidShadingEngine,
            @Autowired(required = false) EventPublisher eventPublisher
    ) {
        this.inferenceEngine = inferenceEngine;
        this.bidShadingEngine = bidShadingEngine;
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
        Object payload = event.payload();
        String tenantId = event.tenantId() != null ? event.tenantId() : "public";
        String slotId = extractString(payload, "slotId", "slot_default");
        String campaignId = extractString(payload, "campaignId", "camp_default");
        String country = extractString(payload, "country", "US");
        String device = extractString(payload, "deviceType", "mobile");
        double price = extractDouble(payload, "price", 1.0);

        if ("auction.win.v1".equals(eventType) && bidShadingEngine != null) {
            String segment = slotId + ":" + country + ":" + device;
            bidShadingEngine.recordWin(segment, price, price * 0.90);
            feedbackCount.incrementAndGet();
            return;
        }

        boolean isImpression = "impression.recorded.v1".equals(eventType);
        boolean isClick = "click.recorded.v1".equals(eventType);
        boolean isConversion = "conversion.approved.v1".equals(eventType);

        if ((isImpression || isClick || isConversion) && inferenceEngine != null) {
            LocalDate today = LocalDate.now();
            TrafficContext ctx = new TrafficContext(
                    "app.publisher.com",
                    1,
                    today,
                    12,
                    today.getDayOfWeek(),
                    country,
                    Set.of()
            );
            Campaign dummyCampaign = new Campaign(
                    campaignId,
                    tenantId,
                    "OnlineFeedback-" + campaignId,
                    today.minusDays(7),
                    today.plusDays(30),
                    BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(Math.max(0.5, price)),
                    Set.of(),
                    Set.of(),
                    Campaign.Status.ACTIVE,
                    Instant.now()
            );
            inferenceEngine.updateModel(
                    ctx,
                    dummyCampaign,
                    BigDecimal.valueOf(0.50),
                    isClick || isConversion,
                    isConversion
            );
            feedbackCount.incrementAndGet();
        }
    }

    public long getFeedbackCount() {
        return feedbackCount.get();
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
