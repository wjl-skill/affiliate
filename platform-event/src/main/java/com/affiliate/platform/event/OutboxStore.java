package com.affiliate.platform.event;

import java.util.List;
import java.util.UUID;

/**
 * 事务发件箱持久化端口接口 (Transactional Outbox Store Interface)
 * <p>
 * 遵循可靠消息最终一致性模式 (Transactional Outbox Pattern)：
 * 业务数据与领域事件在同一个本地数据库事务中落盘，由后台中继作业异步投递至 Kafka，
 * 彻底消除微服务与消息中间件之间的分布式双写不一致。
 */
public interface OutboxStore {

    /**
     * 将领域事件持久化写入发件箱存储
     *
     * @param event 待暂存的领域事件
     */
    void append(DomainEvent<?> event);

    /**
     * 批量获取尚未成功投递至消息总线的待发布事件列表
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
    default void recordFailure(UUID eventId) {}
}
