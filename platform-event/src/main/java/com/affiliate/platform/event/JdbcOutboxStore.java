package com.affiliate.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 基于 PostgreSQL 真实表的事务发件箱持久化存储实现 (PostgreSQL JDBC Outbox Store)
 * <p>
 * 对应 Flyway `event_outbox` 表：
 * 1. 在本地业务事务中将领域事件序列化为 JSONB 存入 `event_outbox`；
 * 2. 轮询时采用高并发数据库关键字 `FOR UPDATE SKIP LOCKED`，允许多个 Worker 实例同时并发拉取待发送事件且互不阻塞。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.kafka-enabled", havingValue = "true")
public class JdbcOutboxStore implements OutboxStore {

    // Spring JDBC 模板
    private final JdbcTemplate jdbc;

    // Jackson JSON 序列化工具
    private final ObjectMapper mapper;

    public JdbcOutboxStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * 将领域事件落库持久化至 event_outbox 表中
     *
     * @param event 待暂存的领域事件
     */
    @Override
    public void append(DomainEvent<?> event) {
        try {
            String sql = "INSERT INTO event_outbox (id, tenant_id, event_type, aggregate_id, payload, occurred_at) " +
                    "VALUES (?, ?, ?, ?, ?::jsonb, ?) " +
                    "ON CONFLICT (id) DO NOTHING";

            jdbc.update(sql,
                    event.eventId(),
                    event.tenantId(),
                    event.eventType(),
                    event.aggregateId(),
                    mapper.writeValueAsString(event.payload()),
                    event.occurredAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("cannot append outbox event", ex);
        }
    }

    /**
     * 高并发批量拉取待发布事件（基于 FOR UPDATE SKIP LOCKED 避免行锁竞争）
     *
     * @param limit 本次批处理拉取的最大数量
     * @return 待发布的事件实体列表
     */
    @Override
    public List<DomainEvent<?>> pending(int limit) {
        // 筛选未发布 (published_at is null) 且失败次数低于 5 次的事件，加行锁并跳过已被其它 Worker 锁定的行
        String sql = "SELECT id, tenant_id, event_type, aggregate_id, payload, occurred_at " +
                "FROM event_outbox WHERE published_at IS NULL AND attempts < 5 " +
                "ORDER BY occurred_at ASC LIMIT ? FOR UPDATE SKIP LOCKED";

        try {
            return jdbc.query(sql, (rs, rowNum) -> {
                UUID id = (UUID) rs.getObject("id");
                String tenantId = rs.getString("tenant_id");
                String eventType = rs.getString("event_type");
                String aggregateId = rs.getString("aggregate_id");
                String payloadJson = rs.getString("payload");
                Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
                Object payload;
                try {
                    payload = mapper.readValue(payloadJson, Object.class);
                } catch (Exception e) {
                    payload = payloadJson;
                }
                return new DomainEvent<>(id, eventType, 1, tenantId, aggregateId, occurredAt, null, payload);
            }, limit);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * 投递成功后更新 published_at 发布时间戳
     *
     * @param eventId 成功发送的事件 ID
     */
    @Override
    public void markPublished(UUID eventId) {
        jdbc.update("UPDATE event_outbox SET published_at = ? WHERE id = ?", Instant.now(), eventId);
    }

    /**
     * 投递失败后递增重试次数 attempts
     *
     * @param eventId 失败的事件 ID
     */
    @Override
    public void recordFailure(UUID eventId) {
        jdbc.update("UPDATE event_outbox SET attempts = attempts + 1 WHERE id = ?", eventId);
    }
}
