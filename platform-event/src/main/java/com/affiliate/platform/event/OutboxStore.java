package com.affiliate.platform.event;

import java.util.List;
import java.util.UUID;

/**
 * 事务发件箱持久化端口接口 (Transactional Outbox Store Interface)
 * <p>
 * 遵循可靠消息最终一致性模式 (Transactional Outbox Pattern)：
 * 业务数据与领域事件在同一个本地数据库事务中落盘，由后台中继作业异步投递至 Kafka，
 * 具备 FOR UPDATE SKIP LOCKED 并发取批、指数退避重试、死信隔离与受权重放功能。
 */
public interface OutboxStore {

    /**
     * 将领域事件持久化写入发件箱存储
     *
     * @param event 待暂存的领域事件
     */
    void append(DomainEvent<?> event);

    /**
     * 批量获取尚未成功投递至消息总线的待发布事件列表 (FOR UPDATE SKIP LOCKED)
     *
     * @param limit 本批次拉取的最大事件条数
     * @return 待发送的领域事件列表
     */
    List<DomainEvent<?>> pending(int limit);

    /**
     * 将指定事件标记为已成功投递
     *
     * @param eventId 已成功推送的事件唯一标识
     */
    void markPublished(UUID eventId);

    /**
     * 记录单次投递失败并累加重试尝试次数
     *
     * @param eventId 投递失败的事件唯一标识
     */
    default void recordFailure(UUID eventId) {
        recordFailure(eventId, null);
    }

    /**
     * 记录单次投递失败并记录末次异常摘要
     *
     * @param eventId   投递失败的事件唯一标识
     * @param lastError 异常错误描述
     */
    default void recordFailure(UUID eventId, String lastError) {}

    /**
     * 将超限重试失败的事件移入死信归档表
     *
     * @param eventId   超限失败的事件唯一标识
     * @param lastError 异常错误描述
     */
    default void moveToDeadLetter(UUID eventId, String lastError) {}

    /**
     * 分页查询当前租户下的死信事件明细
     *
     * @param tenantId 租户唯一标识
     * @param limit    拉取上限
     * @return 死信实体明细列表
     */
    default List<EventOutboxDeadEntity> getDeadLetters(String tenantId, int limit) {
        return List.of();
    }

    /**
     * 定向重新投递死信事件（重置 attempts 为 0 并放回待投递列表）
     *
     * @param eventId 目标死信事件唯一标识
     * @return true 代表重放成功
     */
    default boolean replayDeadLetter(UUID eventId) {
        return false;
    }
}
