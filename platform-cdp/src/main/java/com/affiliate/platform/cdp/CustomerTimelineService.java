package com.affiliate.platform.cdp;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 客户全生命周期 360 度事件时间轴服务 (Customer 360 Event Timeline Service)
 * <p>
 * 追踪记录一方客户全触点行为时间序列：
 * 包含展示曝光、点击、加购、订单购买及退款，支持按时间序列快速检索与受众分析。
 */
@Service
public class CustomerTimelineService {

    public enum EventType {
        /** 广告展示曝光 */
        IMPRESSION,
        /** 广告点击 */
        AD_CLICK,
        /** 加入购物车 */
        ADD_TO_CART,
        /** 交易订单支付 */
        PURCHASE,
        /** 售后退款 */
        REFUND
    }

    public record CustomerEvent(
            String eventId,
            String primaryId,
            EventType type,
            Instant timestamp,
            Map<String, String> payload
    ) {}

    // Key 为 primaryId，Value 为该客户按时间倒序排列的事件列表
    private final ConcurrentMap<String, List<CustomerEvent>> timelines = new ConcurrentHashMap<>();

    /**
     * 追加记录一条客户行为事件
     */
    public CustomerEvent recordEvent(
            String primaryId,
            EventType type,
            Instant timestamp,
            Map<String, String> payload
    ) {
        String eventId = "evt_" + UUID.randomUUID();
        Instant ts = timestamp == null ? Instant.now() : timestamp;
        CustomerEvent event = new CustomerEvent(eventId, primaryId, type, ts, payload == null ? Map.of() : Map.copyOf(payload));

        timelines.computeIfAbsent(primaryId, k -> Collections.synchronizedList(new ArrayList<>())).add(event);
        return event;
    }

    /**
     * 获取指定客户的全量时间轴事件（按时间倒序）
     */
    public List<CustomerEvent> getTimeline(String primaryId) {
        List<CustomerEvent> list = timelines.get(primaryId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            List<CustomerEvent> copy = new ArrayList<>(list);
            copy.sort(Comparator.comparing(CustomerEvent::timestamp).reversed());
            return copy;
        }
    }
}
