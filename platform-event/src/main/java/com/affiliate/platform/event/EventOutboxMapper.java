package com.affiliate.platform.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutboxEntity> {

    @Insert("INSERT INTO event_outbox (id, tenant_id, event_type, aggregate_id, payload, occurred_at) VALUES (#{id}, #{tenantId}, #{eventType}, #{aggregateId}, CAST(#{payload} AS jsonb), #{occurredAt}) ON CONFLICT (id) DO NOTHING")
    int append(EventOutboxEntity entity);

    @Select("SELECT id, tenant_id, event_type, aggregate_id, CAST(payload AS text) AS payload, occurred_at, published_at, attempts, last_error FROM event_outbox WHERE published_at IS NULL AND attempts < 5 ORDER BY occurred_at ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<EventOutboxEntity> pending(int limit);

    @Update("UPDATE event_outbox SET published_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markPublished(UUID id);

    @Update("UPDATE event_outbox SET attempts = attempts + 1 WHERE id = #{id}")
    int recordFailure(UUID id);

    @Update("UPDATE event_outbox SET attempts = attempts + 1, last_error = #{lastError} WHERE id = #{id}")
    int recordFailureWithCause(@Param("id") UUID id, @Param("lastError") String lastError);

    @Insert("INSERT INTO event_outbox_dead (id, tenant_id, event_type, aggregate_id, payload, occurred_at, failed_at, attempts, last_error) " +
            "SELECT id, tenant_id, event_type, aggregate_id, payload, occurred_at, CURRENT_TIMESTAMP, attempts, #{lastError} " +
            "FROM event_outbox WHERE id = #{id} " +
            "ON CONFLICT (id) DO UPDATE SET failed_at = CURRENT_TIMESTAMP, last_error = #{lastError}")
    int moveToDeadLetter(@Param("id") UUID id, @Param("lastError") String lastError);

    @Delete("DELETE FROM event_outbox WHERE id = #{id}")
    int deleteFromOutbox(@Param("id") UUID id);

    @Select("SELECT id, tenant_id, event_type, aggregate_id, CAST(payload AS text) AS payload, occurred_at, failed_at, attempts, last_error, replayed_at " +
            "FROM event_outbox_dead WHERE tenant_id = #{tenantId} ORDER BY failed_at DESC LIMIT #{limit}")
    List<EventOutboxDeadEntity> selectDeadLetters(@Param("tenantId") String tenantId, @Param("limit") int limit);

    @Select("SELECT id, tenant_id, event_type, aggregate_id, CAST(payload AS text) AS payload, occurred_at, failed_at, attempts, last_error, replayed_at " +
            "FROM event_outbox_dead WHERE id = #{id}")
    EventOutboxDeadEntity selectDeadLetterById(@Param("id") UUID id);

    @Update("UPDATE event_outbox_dead SET replayed_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markDeadLetterReplayed(@Param("id") UUID id);

    @Insert("INSERT INTO event_outbox (id, tenant_id, event_type, aggregate_id, payload, occurred_at, attempts) " +
            "SELECT id, tenant_id, event_type, aggregate_id, payload, occurred_at, 0 " +
            "FROM event_outbox_dead WHERE id = #{id} " +
            "ON CONFLICT (id) DO UPDATE SET attempts = 0, published_at = NULL, last_error = NULL")
    int reinsertToOutbox(@Param("id") UUID id);
}
