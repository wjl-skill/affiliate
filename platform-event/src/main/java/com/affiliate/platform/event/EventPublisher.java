package com.affiliate.platform.event;

/**
 * 领域事件发布器顶级抽象契约 (Event Publisher Interface)
 * <p>
 * 为各领域业务服务提供统一的事件发布接口，屏蔽具体底层存储与投递中继细节（内存或 Kafka）。
 */
public interface EventPublisher {

    /**
     * 将业务领域事件发布投递至事件总线或事务发件箱
     *
     * @param event 待发布的领域事件实例
     */
    void publish(DomainEvent<?> event);
}
