package com.affiliate.platform.event;

import java.util.function.Consumer;

/**
 * 领域事件发布器顶级抽象契约 (Event Publisher & Real-Time Subscriber Bus Interface)
 * <p>
 * 为各领域业务服务提供统一的事件发布与流式订阅接口，屏蔽具体底层存储与投递中继细节（内存、Spring Event 或 Kafka）。
 */
public interface EventPublisher {

    /**
     * 将业务领域事件发布投递至事件总线、事务发件箱及实时流订阅者
     *
     * @param event 待发布的领域事件实例
     */
    void publish(DomainEvent<?> event);

    /**
     * 注册实时领域事件消费者（用于进程内或流式管道实时驱动财务记账、ClickHouse 入库、CDP 画像与 AI 在线反馈）
     *
     * @param subscriber 事件消费者回调
     */
    default void subscribe(Consumer<DomainEvent<?>> subscriber) {
    }
}
