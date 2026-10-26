package com.affiliate.platform.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

/**
 * 事务发件箱死信实体 (Transactional Outbox Dead Letter Entity)
 * <p>
 * 当发件箱事件多次投递失败（attempts >= 最大阈值）时，由中继调度器移入本表，解除待投递阻塞，
 * 支持带权限审查的死信查询与定向重新投递重放。
 */
@TableName("event_outbox_dead")
public class EventOutboxDeadEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String tenantId;
    private String eventType;
    private String aggregateId;
    private String payload;
    private Instant occurredAt;
    private Instant failedAt;
    private Integer attempts;
    private String lastError;
    private Instant replayedAt;

    public EventOutboxDeadEntity() {}

    public EventOutboxDeadEntity(
            UUID id,
            String tenantId,
            String eventType,
            String aggregateId,
            String payload,
            Instant occurredAt,
            Instant failedAt,
            Integer attempts,
            String lastError,
            Instant replayedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.failedAt = failedAt;
        this.attempts = attempts;
        this.lastError = lastError;
        this.replayedAt = replayedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getReplayedAt() { return replayedAt; }
    public void setReplayedAt(Instant replayedAt) { this.replayedAt = replayedAt; }
}
