package com.affiliate.platform.cdp;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDP 实时画像特征领域事件订阅器 (CDP Real-Time User Trait Event Subscriber)
 * <p>
 * 监听 {@code impression.recorded.v1}、{@code click.recorded.v1}、{@code conversion.approved.v1} 事件，
 * 毫秒级驱动 {@link RealtimeEventTraitEngine} 更新用户 7 天滚动活跃度、品类偏好及高价值人群标签。
 */
@Component
public class CdpDomainEventSubscriber {

    private final RealtimeEventTraitEngine traitEngine;
    private final EventPublisher eventPublisher;
    private final AtomicLong processedTraitEvents = new AtomicLong(0);

    @Autowired
    public CdpDomainEventSubscriber(
            RealtimeEventTraitEngine traitEngine,
            @Autowired(required = false) EventPublisher eventPublisher
    ) {
        this.traitEngine = traitEngine;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void initSubscription() {
        if (eventPublisher != null) {
            eventPublisher.subscribe(this::onDomainEvent);
        }
    }

    public void onDomainEvent(DomainEvent<?> event) {
        if (event == null || event.eventType() == null || traitEngine == null) {
            return;
        }
        String eventType = event.eventType();
        Object payload = event.payload();
        String userId = extractString(payload, "userId",
                extractString(payload, "affiliateId",
                        extractString(payload, "slotId", null)));
        if (userId == null || userId.isBlank()) {
            return;
        }

        String category = extractString(payload, "category", "adtech");
        String device = extractString(payload, "deviceType", "mobile");
        Instant ts = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        switch (eventType) {
            case "impression.recorded.v1" -> {
                traitEngine.ingestEvent(userId, RealtimeEventTraitEngine.EventType.PAGEVIEW, BigDecimal.ZERO, category, device, ts);
                processedTraitEvents.incrementAndGet();
            }
            case "click.recorded.v1" -> {
                traitEngine.ingestEvent(userId, RealtimeEventTraitEngine.EventType.CLICK, BigDecimal.ZERO, category, device, ts);
                processedTraitEvents.incrementAndGet();
            }
            case "conversion.approved.v1" -> {
                double rev = extractDouble(payload, "revenue", extractDouble(payload, "saleAmount", 25.0));
                traitEngine.ingestEvent(userId, RealtimeEventTraitEngine.EventType.PURCHASE, BigDecimal.valueOf(rev), category, device, ts);
                processedTraitEvents.incrementAndGet();
            }
        }
    }

    public long getProcessedTraitEvents() {
        return processedTraitEvents.get();
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
