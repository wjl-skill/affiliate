package com.affiliate.platform.event;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 事务发件箱死信队列运维服务 (Outbox Dead Letter Queue Service)
 */
@Service
public class OutboxDeadLetterService {

    private final OutboxStore outboxStore;

    public OutboxDeadLetterService(OutboxStore outboxStore) {
        this.outboxStore = outboxStore;
    }

    /**
     * 查询指定租户下的死信列表
     */
    public List<EventOutboxDeadEntity> getDeadLetters(String tenantId, int limit) {
        return outboxStore.getDeadLetters(tenantId, limit);
    }

    /**
     * 重放指定死信事件（放回 pending 重试）
     */
    public boolean replay(UUID deadEventId) {
        return outboxStore.replayDeadLetter(deadEventId);
    }
}
