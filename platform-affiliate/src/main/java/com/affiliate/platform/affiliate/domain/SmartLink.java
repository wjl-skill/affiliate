package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

/**
 * 智能分流链接领域实体 (SmartLink Domain Record)
 * <p>
 * 工业级 TDS (Traffic Distribution System) 核心：
 * 渠道仅推广单条统一链接，系统根据访客环境及候选 Offer 的实时 EPC 表现动态分流。
 */
public record SmartLink(
        String id,
        String tenantId,
        @NotBlank String name,
        String category,
        @NotEmpty List<String> targetOfferIds,
        RoutingStrategy routingStrategy,
        String fallbackOfferId,
        Instant createdAt
) {
    public SmartLink {
        targetOfferIds = targetOfferIds == null ? List.of() : List.copyOf(targetOfferIds);
        routingStrategy = routingStrategy == null ? RoutingStrategy.HIGHEST_EPC : routingStrategy;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum RoutingStrategy {
        /** 收益最大化：动态优先路由至近期历史 EPC 最高的可用 Offer */
        HIGHEST_EPC,
        /** 轮询均摊测试 */
        ROUND_ROBIN
    }
}
