package com.affiliate.platform.cdp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * CDP 实时流式受众圈选与进出事件引擎 (Real-time Audience Segment Engine)
 * <p>
 * 商业级 CDP 核心受众引擎：
 * 1. 注册与管理多维度动态受众圈选规则 (AudienceSegmentRule)；
 * 2. 对用户的实时行为画像快照进行毫秒级规则匹配与受众打标；
 * 3. 具备增量进出差异计算能力 (Differential Segment Evaluation)：
 *    自动探测用户最新“进入 (Entered)”或“退出 (Exited)”的受众分群，驱动下游营销自动化与 DSP 定向同步；
 * 4. 实时维护各受众群体的活跃成员规模计数器。
 */
@Service
public class AudienceSegmentEngine {

    /**
     * 动态受众圈选规则定义
     */
    public record AudienceSegmentRule(
            String segmentId,
            String name,
            BigDecimal minSpend,
            Integer minPurchases,
            Set<String> requiredTraits,
            Set<String> excludedTraits,
            Predicate<RealtimeEventTraitEngine.UserBehaviorSnapshot> customFilter
    ) {
        public AudienceSegmentRule {
            requiredTraits = requiredTraits == null ? Set.of() : Set.copyOf(requiredTraits);
            excludedTraits = excludedTraits == null ? Set.of() : Set.copyOf(excludedTraits);
        }

        public boolean matches(RealtimeEventTraitEngine.UserBehaviorSnapshot snapshot) {
            if (snapshot == null) return false;

            // 1. 累计消费过滤
            if (minSpend != null && (snapshot.totalSpend() == null || snapshot.totalSpend().compareTo(minSpend) < 0)) {
                return false;
            }

            // 2. 购买次数过滤
            if (minPurchases != null && snapshot.purchaseCount() < minPurchases) {
                return false;
            }

            // 3. 必须包含的标签
            if (!requiredTraits.isEmpty()) {
                Set<String> userTraits = snapshot.dynamicTraits();
                if (userTraits == null || !userTraits.containsAll(requiredTraits)) {
                    return false;
                }
            }

            // 4. 排除的标签
            if (!excludedTraits.isEmpty() && snapshot.dynamicTraits() != null) {
                if (snapshot.dynamicTraits().stream().anyMatch(excludedTraits::contains)) {
                    return false;
                }
            }

            // 5. 自定义谓词
            if (customFilter != null && !customFilter.test(snapshot)) {
                return false;
            }

            return true;
        }
    }

    // 已注册的受众规则表：Key 为 segmentId
    private final ConcurrentMap<String, AudienceSegmentRule> rules = new ConcurrentHashMap<>();

    // 用户当前已入组的受众集合：Key 为 primaryId, Value 为 Set of segmentIds
    private final ConcurrentMap<String, Set<String>> userSegmentMemberships = new ConcurrentHashMap<>();

    // 各受众群体活跃成员计数器
    private final ConcurrentMap<String, AtomicLong> segmentPopulation = new ConcurrentHashMap<>();

    public void registerRule(AudienceSegmentRule rule) {
        if (rule != null && rule.segmentId() != null) {
            rules.put(rule.segmentId(), rule);
            segmentPopulation.computeIfAbsent(rule.segmentId(), k -> new AtomicLong(0));
        }
    }

    public void removeRule(String segmentId) {
        if (segmentId != null) {
            rules.remove(segmentId);
            segmentPopulation.remove(segmentId);
        }
    }

    public Optional<AudienceSegmentRule> getRule(String segmentId) {
        return Optional.ofNullable(rules.get(segmentId));
    }

    /**
     * 评估用户当前匹配的全部受众分群集合
     */
    public Set<String> evaluateSegments(RealtimeEventTraitEngine.UserBehaviorSnapshot snapshot) {
        if (snapshot == null) return Set.of();

        Set<String> matched = new HashSet<>();
        for (AudienceSegmentRule rule : rules.values()) {
            if (rule.matches(snapshot)) {
                matched.add(rule.segmentId());
            }
        }
        return Collections.unmodifiableSet(matched);
    }

    /**
     * 增量计算用户受众进出变化 (Differential Evaluation)
     *
     * @param primaryId 客户主标识
     * @param snapshot  最新画像快照
     * @return 进出差异结果
     */
    public SegmentDifferentialResult evaluateDifferential(String primaryId, RealtimeEventTraitEngine.UserBehaviorSnapshot snapshot) {
        if (primaryId == null || primaryId.isBlank()) {
            return SegmentDifferentialResult.empty();
        }

        Set<String> newSegments = evaluateSegments(snapshot);
        Set<String> oldSegments = userSegmentMemberships.getOrDefault(primaryId, Set.of());

        // 计算新进入的分群
        Set<String> entered = new HashSet<>(newSegments);
        entered.removeAll(oldSegments);

        // 计算新退出的分群
        Set<String> exited = new HashSet<>(oldSegments);
        exited.removeAll(newSegments);

        // 更新状态与人数统计
        if (!entered.isEmpty() || !exited.isEmpty()) {
            userSegmentMemberships.put(primaryId, Set.copyOf(newSegments));

            for (String seg : entered) {
                segmentPopulation.computeIfAbsent(seg, k -> new AtomicLong(0)).incrementAndGet();
            }
            for (String seg : exited) {
                AtomicLong pop = segmentPopulation.get(seg);
                if (pop != null && pop.get() > 0) {
                    pop.decrementAndGet();
                }
            }
        }

        return new SegmentDifferentialResult(
                primaryId,
                Set.copyOf(newSegments),
                Set.copyOf(entered),
                Set.copyOf(exited)
        );
    }

    public Set<String> getUserSegments(String primaryId) {
        return userSegmentMemberships.getOrDefault(primaryId, Set.of());
    }

    public long getSegmentCount(String segmentId) {
        AtomicLong pop = segmentPopulation.get(segmentId);
        return pop == null ? 0 : pop.get();
    }

    public void clear() {
        rules.clear();
        userSegmentMemberships.clear();
        segmentPopulation.clear();
    }

    public record SegmentDifferentialResult(
            String primaryId,
            Set<String> currentSegments,
            Set<String> enteredSegments,
            Set<String> exitedSegments
    ) {
        public static SegmentDifferentialResult empty() {
            return new SegmentDifferentialResult("", Set.of(), Set.of(), Set.of());
        }

        public boolean hasChanges() {
            return !enteredSegments.isEmpty() || !exitedSegments.isEmpty();
        }
    }
}
