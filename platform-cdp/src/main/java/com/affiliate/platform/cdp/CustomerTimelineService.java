package com.affiliate.platform.cdp;

import com.affiliate.platform.entity.CdpCustomerEventEntity;
import com.affiliate.platform.mapper.CdpCustomerEventMapper;
import com.affiliate.platform.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 客户 360 时间轴；生产环境事件通过 MyBatis-Plus 落盘。 */
@Service
public class CustomerTimelineService {
    public enum EventType { IMPRESSION, AD_CLICK, ADD_TO_CART, PURCHASE, REFUND }
    public record CustomerEvent(String eventId, String primaryId, EventType type, Instant timestamp, Map<String, String> payload) {}

    private final CdpCustomerEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, List<CustomerEvent>> fallback = new ConcurrentHashMap<>();

    public CustomerTimelineService() { this(null, null); }

    @Autowired
    public CustomerTimelineService(@Autowired(required = false) CdpCustomerEventMapper eventMapper,
                                   @Autowired(required = false) ObjectMapper objectMapper) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public CustomerEvent recordEvent(String primaryId, EventType type, Instant timestamp, Map<String, String> payload) {
        if (primaryId == null || primaryId.isBlank() || type == null) throw new IllegalArgumentException("primaryId and type are required");
        Instant ts = timestamp == null ? Instant.now() : timestamp;
        CustomerEvent event = new CustomerEvent("evt_" + UUID.randomUUID(), primaryId, type, ts, payload == null ? Map.of() : Map.copyOf(payload));
        if (eventMapper != null) {
            try {
                eventMapper.insert(new CdpCustomerEventEntity(event.eventId(), tenant(), primaryId, type.name(), ts, objectMapper.writeValueAsString(event.payload())));
            } catch (Exception e) { throw new IllegalStateException("cannot persist customer timeline event", e); }
        } else fallback.computeIfAbsent(key(primaryId), ignored -> Collections.synchronizedList(new ArrayList<>())).add(event);
        return event;
    }

    public List<CustomerEvent> getTimeline(String primaryId) {
        if (primaryId == null || primaryId.isBlank()) return List.of();
        if (eventMapper != null) {
            List<CdpCustomerEventEntity> entities = eventMapper.selectList(new LambdaQueryWrapper<CdpCustomerEventEntity>()
                    .eq(CdpCustomerEventEntity::getTenantId, tenant()).eq(CdpCustomerEventEntity::getPrimaryId, primaryId)
                    .orderByDesc(CdpCustomerEventEntity::getEventAt));
            return entities.stream().map(this::fromEntity).toList();
        }
        List<CustomerEvent> list = fallback.get(key(primaryId));
        if (list == null || list.isEmpty()) return List.of();
        synchronized (list) { return list.stream().sorted(Comparator.comparing(CustomerEvent::timestamp).reversed()).toList(); }
    }

    private CustomerEvent fromEntity(CdpCustomerEventEntity e) {
        try {
            Map<String, String> payload = e.getPayload() == null ? Map.of() : objectMapper.readValue(e.getPayload(), new TypeReference<>() {});
            return new CustomerEvent(e.getEventId(), e.getPrimaryId(), EventType.valueOf(e.getEventType()), e.getEventAt(), payload);
        } catch (Exception ex) { throw new IllegalStateException("cannot deserialize customer timeline event", ex); }
    }
    private static String key(String primaryId) { return tenant() + ":" + primaryId; }
    private static String tenant() { return TenantContext.get() == null ? "public" : TenantContext.required(); }
}
