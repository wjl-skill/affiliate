package com.affiliate.platform.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 平台通用领域事件实体 (Domain Event Record)
 * <p>
 * 遵循 CloudEvents 与事件驱动架构规范：
 * 包含全局唯一事件 ID、事件类型、契约版本、所属租户、聚合根 ID、发生时间戳、分布式链路追踪 ID 及业务载荷。
 *
 * @param eventId       事件唯一 UUID
 * @param eventType     事件类型标识（如 "creative.created.v1", "auction.win.v1"）
 * @param schemaVersion 领域事件契约大版本号
 * @param tenantId      事件所属租户空间标识
 * @param aggregateId   产生该事件的业务实体主键（如 creativeId, campaignId）
 * @param occurredAt    事件在系统内部实际发生的时间戳
 * @param traceId       全链路分布式调用链追踪 Trace ID（便于 SkyWalking/Zipkin 串联）
 * @param payload       事件携带的业务领域对象或只读快照
 * @param <T>           载荷实体泛型类型
 */
public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String tenantId,
        String aggregateId,
        Instant occurredAt,
        String traceId,
        T payload
) {
    /**
     * 快速构建新发生的领域事件实例
     *
     * @param eventType   事件类型标识
     * @param tenantId    租户标识
     * @param aggregateId 聚合根主键标识
     * @param payload     业务数据载荷
     * @param <T>         数据泛型
     * @return 预置了随机 UUID 与当前系统发生时间的 DomainEvent 实例
     */
    public static <T> DomainEvent<T> create(String eventType, String tenantId, String aggregateId, T payload) {
        return new DomainEvent<>(UUID.randomUUID(), eventType, 1, tenantId, aggregateId, Instant.now(), null, payload);
    }
}
