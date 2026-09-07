package com.affiliate.platform.dsp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 广告组领域实体 (DSP AdGroup Domain Record)
 * <p>
 * 介于 Campaign（活动总预算）与 Ad（具体素材）之间的核心执行单元：
 * 聚合具体的地域、设备、时段、人群包等定向条件与特定出价策略。
 */
public record AdGroup(
        String id,
        @NotBlank String campaignId,
        @NotBlank String name,
        @NotNull BiddingStrategy biddingStrategy,
        Set<String> targetGeos,
        Set<Integer> targetDeviceTypes,
        Set<String> targetDomains,
        Dayparting dayparting,
        Set<String> includeSegments,
        Set<String> excludeSegments,
        Status status,
        Instant createdAt
) {
    public AdGroup {
        targetGeos = targetGeos == null ? Set.of() : targetGeos.stream().map(String::toUpperCase).collect(Collectors.toUnmodifiableSet());
        targetDeviceTypes = targetDeviceTypes == null ? Set.of() : Set.copyOf(targetDeviceTypes);
        targetDomains = targetDomains == null ? Set.of() : targetDomains.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        dayparting = dayparting == null ? Dayparting.allHours() : dayparting;
        includeSegments = includeSegments == null ? Set.of() : Set.copyOf(includeSegments);
        excludeSegments = excludeSegments == null ? Set.of() : Set.copyOf(excludeSegments);
        status = status == null ? Status.DRAFT : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum Status {
        DRAFT,
        ACTIVE,
        PAUSED,
        ENDED
    }

    /**
     * 判断当前广告组是否符合流量上下文定向规则
     */
    public boolean matches(TrafficContext ctx) {
        if (status != Status.ACTIVE) {
            return false;
        }

        // 1. 时段排期定向检查
        if (!dayparting.allows(ctx.dayOfWeek(), ctx.hourOfDay())) {
            return false;
        }

        // 2. 地理区域国家定向检查 (空集合代表不限)
        if (!targetGeos.isEmpty() && (ctx.country().isBlank() || !targetGeos.contains(ctx.country()))) {
            return false;
        }

        // 3. 设备类型定向检查
        if (!targetDeviceTypes.isEmpty() && !targetDeviceTypes.contains(ctx.deviceType())) {
            return false;
        }

        // 4. 域名定向检查
        if (!targetDomains.isEmpty() && (ctx.domain().isBlank() || !targetDomains.contains(ctx.domain()))) {
            return false;
        }

        // 5. 排除受众客群检查 (命中任何排除客群即过滤)
        if (!excludeSegments.isEmpty() && ctx.userSegments().stream().anyMatch(excludeSegments::contains)) {
            return false;
        }

        // 6. 包含受众客群检查 (若设置了目标受众，必须命中至少一个)
        if (!includeSegments.isEmpty() && ctx.userSegments().stream().noneMatch(includeSegments::contains)) {
            return false;
        }

        return true;
    }
}
