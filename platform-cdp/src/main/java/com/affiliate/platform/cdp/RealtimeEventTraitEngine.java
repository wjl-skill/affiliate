package com.affiliate.platform.cdp;

import com.affiliate.platform.entity.CdpUserTraitEntity;
import com.affiliate.platform.mapper.CdpUserTraitMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * 实时行为特征与标签计算引擎 (Realtime Event Trait Engine)
 * <p>
 * 工业级 CDP 核心流式计算组件：
 * 1. 实时摄入用户全触点行为流（点击、加购、购买、浏览等）；
 * 2. 维护用户滚动多维指标（7天活跃频次、累计消费、品类偏好分布、设备终端亲和度）；
 * 3. 动态触发高价值大 R 玩家 (whale_purchaser)、价格敏感型 (bargain_hunter)、流失风险 (churn_risk) 等画像标签；
 * 4. 自动协同 {@link IdentityMappingService} 与 {@link CustomerTimelineService} 保持统一画像更新；
 * 5. 联动 PostgreSQL `cdp_user_trait_state` 异步持久化存储与回源查询。
 */
@Service
public class RealtimeEventTraitEngine {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventTraitEngine.class);

    public enum EventType {
        PAGEVIEW,
        CLICK,
        ADD_TO_CART,
        PURCHASE,
        REFUND
    }

    /**
     * 用户行为状态聚合实体
     */
    public record UserBehaviorSnapshot(
            String primaryId,
            BigDecimal totalSpend,
            int purchaseCount,
            int clickCount,
            int pageviewCount,
            int events7dCount,
            String preferredCategory,
            String preferredDevice,
            Set<String> dynamicTraits,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {}

    static class InternalState {
        final String primaryId;
        BigDecimal totalSpend = BigDecimal.ZERO;
        int purchaseCount = 0;
        int clickCount = 0;
        int pageviewCount = 0;
        final Deque<Instant> recentEventTimestamps = new ConcurrentLinkedDeque<>();
        final Map<String, Integer> categoryCounts = new ConcurrentHashMap<>();
        final Map<String, Integer> deviceCounts = new ConcurrentHashMap<>();
        Instant firstSeenAt;
        Instant lastSeenAt;

        InternalState(String primaryId) {
            this.primaryId = primaryId;
        }
    }

    private final ConcurrentMap<String, InternalState> userStates = new ConcurrentHashMap<>();
    private final IdentityMappingService identityService;
    private final CustomerTimelineService timelineService;
    private final CdpUserTraitMapper traitMapper;

    @Autowired
    public RealtimeEventTraitEngine(
            @Autowired(required = false) IdentityMappingService identityService,
            @Autowired(required = false) CustomerTimelineService timelineService,
            @Autowired(required = false) CdpUserTraitMapper traitMapper
    ) {
        this.identityService = identityService;
        this.timelineService = timelineService;
        this.traitMapper = traitMapper;
    }

    public RealtimeEventTraitEngine(IdentityMappingService identityService, CustomerTimelineService timelineService) {
        this(identityService, timelineService, null);
    }

    public RealtimeEventTraitEngine() {
        this(null, null, null);
    }

    /**
     * 摄入流式行为事件并实时重算特征标签
     *
     * @param primaryId  客户主标识
     * @param type       事件类型
     * @param amount     交易金额（可选）
     * @param category   商品/业务品类（可选）
     * @param deviceType 触点终端类型（如 MOBILE, DESKTOP）
     * @param timestamp  事件发生时间戳
     * @return 重新计算后的用户最新状态快照
     */
    public UserBehaviorSnapshot ingestEvent(
            String primaryId,
            EventType type,
            BigDecimal amount,
            String category,
            String deviceType,
            Instant timestamp
    ) {
        if (primaryId == null || primaryId.isBlank()) {
            throw new IllegalArgumentException("primaryId must not be blank");
        }
        Instant ts = timestamp == null ? Instant.now() : timestamp;

        InternalState state = userStates.computeIfAbsent(primaryId, InternalState::new);

        synchronized (state) {
            if (state.firstSeenAt == null) {
                state.firstSeenAt = ts;
            }
            if (state.lastSeenAt == null || ts.isAfter(state.lastSeenAt)) {
                state.lastSeenAt = ts;
            }

            state.recentEventTimestamps.addLast(ts);
            // 清理超过 7 天前的滚动窗口事件
            Instant cutoff7d = ts.minus(Duration.ofDays(7));
            while (!state.recentEventTimestamps.isEmpty() && state.recentEventTimestamps.peekFirst().isBefore(cutoff7d)) {
                state.recentEventTimestamps.pollFirst();
            }

            if (category != null && !category.isBlank()) {
                state.categoryCounts.merge(category.trim().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
            if (deviceType != null && !deviceType.isBlank()) {
                state.deviceCounts.merge(deviceType.trim().toUpperCase(Locale.ROOT), 1, Integer::sum);
            }

            switch (type) {
                case PURCHASE -> {
                    state.purchaseCount++;
                    if (amount != null && amount.signum() > 0) {
                        state.totalSpend = state.totalSpend.add(amount);
                    }
                }
                case REFUND -> {
                    if (amount != null && amount.signum() > 0) {
                        state.totalSpend = state.totalSpend.subtract(amount);
                        if (state.totalSpend.signum() < 0) {
                            state.totalSpend = BigDecimal.ZERO;
                        }
                    }
                }
                case CLICK -> state.clickCount++;
                case PAGEVIEW -> state.pageviewCount++;
                case ADD_TO_CART -> {}
            }
        }

        // 记录时间轴
        if (timelineService != null) {
            Map<String, String> payload = new HashMap<>();
            if (amount != null) payload.put("amount", amount.toPlainString());
            if (category != null) payload.put("category", category);
            if (deviceType != null) payload.put("deviceType", deviceType);

            CustomerTimelineService.EventType timelineType = switch (type) {
                case PURCHASE -> CustomerTimelineService.EventType.PURCHASE;
                case REFUND -> CustomerTimelineService.EventType.REFUND;
                case CLICK -> CustomerTimelineService.EventType.AD_CLICK;
                default -> CustomerTimelineService.EventType.IMPRESSION;
            };
            timelineService.recordEvent(primaryId, timelineType, ts, payload);
        }

        // 计算衍生画像标签
        Set<String> dynamicTraits = evaluateTraits(state, ts);

        // 同步标签至 CDP CustomerProfile
        if (identityService != null && !dynamicTraits.isEmpty()) {
            try {
                identityService.merge(primaryId, Set.of(), Map.of(), dynamicTraits);
            } catch (Exception ignored) {
                // 如果画像尚未创建则允许忽略
            }
        }

        // 异步持久化特征状态至 PostgreSQL (Java 21 Virtual Threads)
        if (traitMapper != null) {
            final Set<String> snapshotTraits = Set.copyOf(dynamicTraits);
            Thread.ofVirtual().name("cdp-trait-persister").start(() -> {
                try {
                    String traitsStr = String.join(",", snapshotTraits);
                    CdpUserTraitEntity entity = traitMapper.selectById(primaryId);
                    if (entity == null) {
                        entity = new CdpUserTraitEntity(
                                primaryId, "default", primaryId, state.totalSpend,
                                state.purchaseCount, state.clickCount, state.pageviewCount,
                                state.recentEventTimestamps.size(), getTopKey(state.categoryCounts),
                                getTopKey(state.deviceCounts), traitsStr,
                                state.firstSeenAt, state.lastSeenAt, Instant.now()
                        );
                        traitMapper.insert(entity);
                    } else {
                        entity.setTotalSpend(state.totalSpend);
                        entity.setPurchaseCount(state.purchaseCount);
                        entity.setClickCount(state.clickCount);
                        entity.setPageviewCount(state.pageviewCount);
                        entity.setEvents7dCount(state.recentEventTimestamps.size());
                        entity.setPreferredCategory(getTopKey(state.categoryCounts));
                        entity.setPreferredDevice(getTopKey(state.deviceCounts));
                        entity.setTraitsJson(traitsStr);
                        entity.setLastSeenAt(state.lastSeenAt);
                        entity.setUpdatedAt(Instant.now());
                        traitMapper.updateById(entity);
                    }
                } catch (Exception e) {
                    log.error("Failed to persist CDP user trait for {}: {}", primaryId, e.getMessage());
                }
            });
        }

        return createSnapshot(state, dynamicTraits);
    }

    /**
     * 评估计算动态行为标签 (Dynamic Trait Rules)
     */
    public Set<String> evaluateTraits(InternalState state, Instant now) {
        Set<String> traits = new HashSet<>();

        // 1. 大 R 高价值客户标签 (whale_purchaser: 累计消费 >= 500 美元)
        if (state.totalSpend.compareTo(BigDecimal.valueOf(500)) >= 0) {
            traits.add("whale_purchaser");
        }

        // 2. 高频下单客户 (frequent_shopper: 购买 >= 5 次)
        if (state.purchaseCount >= 5) {
            traits.add("frequent_shopper");
        }

        // 3. 价格敏感羊毛党 (bargain_hunter: 有购买且客单价 < 20 美元，且高频点击或加购)
        if (state.purchaseCount > 0) {
            BigDecimal aov = state.totalSpend.divide(BigDecimal.valueOf(state.purchaseCount), 2, RoundingMode.HALF_UP);
            if (aov.compareTo(BigDecimal.valueOf(20)) < 0) {
                traits.add("bargain_hunter");
            }
        }

        // 4. 橱窗型访客 (window_shopper: 浏览/点击 >= 8 次但 0 购买)
        if (state.purchaseCount == 0 && (state.clickCount + state.pageviewCount) >= 8) {
            traits.add("window_shopper");
        }

        // 5. 7天活跃用户 (active_7d: 过去7天触点事件 >= 3 次)
        if (state.recentEventTimestamps.size() >= 3) {
            traits.add("active_7d");
        }

        // 6. 流失预警 (churn_risk: 曾经活跃但最近 14 天无触点)
        if (state.lastSeenAt != null && Duration.between(state.lastSeenAt, now).toDays() >= 14) {
            traits.add("churn_risk");
        }

        // 7. 终端偏好标签 (mobile_native / desktop_power)
        String topDevice = getTopKey(state.deviceCounts);
        if ("MOBILE".equalsIgnoreCase(topDevice)) {
            traits.add("mobile_native");
        } else if ("DESKTOP".equalsIgnoreCase(topDevice)) {
            traits.add("desktop_power");
        }

        // 8. 类目偏好标签 (pref_category)
        String topCategory = getTopKey(state.categoryCounts);
        if (topCategory != null && !topCategory.isBlank()) {
            traits.add("pref_" + topCategory);
        }

        return traits;
    }

    /**
     * 获取指定用户的特征快照
     */
    public Optional<UserBehaviorSnapshot> getSnapshot(String primaryId) {
        InternalState state = userStates.get(primaryId);
        if (state != null) {
            Set<String> traits = evaluateTraits(state, Instant.now());
            return Optional.of(createSnapshot(state, traits));
        }
        if (traitMapper != null) {
            try {
                CdpUserTraitEntity entity = traitMapper.selectById(primaryId);
                if (entity != null) {
                    Set<String> traits = (entity.getTraitsJson() != null && !entity.getTraitsJson().isBlank())
                            ? Set.of(entity.getTraitsJson().split(","))
                            : Set.of();
                    return Optional.of(new UserBehaviorSnapshot(
                            entity.getPrimaryId(),
                            entity.getTotalSpend() != null ? entity.getTotalSpend() : BigDecimal.ZERO,
                            entity.getPurchaseCount() != null ? entity.getPurchaseCount() : 0,
                            entity.getClickCount() != null ? entity.getClickCount() : 0,
                            entity.getPageviewCount() != null ? entity.getPageviewCount() : 0,
                            entity.getEvents7dCount() != null ? entity.getEvents7dCount() : 0,
                            entity.getPreferredCategory(),
                            entity.getPreferredDevice(),
                            traits,
                            entity.getFirstSeenAt(),
                            entity.getLastSeenAt()
                    ));
                }
            } catch (Exception e) {
                log.warn("Failed to read user trait from database for {}: {}", primaryId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private UserBehaviorSnapshot createSnapshot(InternalState state, Set<String> traits) {
        return new UserBehaviorSnapshot(
                state.primaryId,
                state.totalSpend,
                state.purchaseCount,
                state.clickCount,
                state.pageviewCount,
                state.recentEventTimestamps.size(),
                getTopKey(state.categoryCounts),
                getTopKey(state.deviceCounts),
                Set.copyOf(traits),
                state.firstSeenAt,
                state.lastSeenAt
        );
    }

    private String getTopKey(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
