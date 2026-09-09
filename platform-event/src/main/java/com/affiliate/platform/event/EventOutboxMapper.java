package com.affiliate.platform.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.UUID;

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutboxEntity> {
    @Insert("INSERT INTO event_outbox (id, tenant_id, event_type, aggregate_id, payload, occurred_at) VALUES (#{id}, #{tenantId}, #{eventType}, #{aggregateId}, CAST(#{payload} AS jsonb), #{occurredAt}) ON CONFLICT (id) DO NOTHING")
    int append(EventOutboxEntity entity);

    @Select("SELECT id, tenant_id, event_type, aggregate_id, CAST(payload AS text) AS payload, occurred_at, published_at, attempts FROM event_outbox WHERE published_at IS NULL AND attempts < 5 ORDER BY occurred_at ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<EventOutboxEntity> pending(int limit);

    @Update("UPDATE event_outbox SET published_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markPublished(UUID id);

    @Update("UPDATE event_outbox SET attempts = attempts + 1 WHERE id = #{id}")
    int recordFailure(UUID id);
}
