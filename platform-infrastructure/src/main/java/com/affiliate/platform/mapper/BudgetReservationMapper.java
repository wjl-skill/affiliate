package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.BudgetReservationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Mapper
public interface BudgetReservationMapper extends BaseMapper<BudgetReservationEntity> {
    @Update("UPDATE budget_reservation SET status='CONFIRMED', confirmed_at=#{confirmedAt} WHERE id=#{id} AND status='RESERVED'")
    int confirm(UUID id, Instant confirmedAt);

    @Update("UPDATE budget_reservation SET status='RELEASED' WHERE id=#{id} AND status='RESERVED'")
    int release(UUID id);

    @Select("SELECT id, tenant_id, campaign_id, user_id, amount, status, created_at, confirmed_at, expires_at FROM budget_reservation WHERE status='RESERVED' AND expires_at < #{now} ORDER BY expires_at ASC FOR UPDATE SKIP LOCKED")
    List<BudgetReservationEntity> findExpired(Instant now);

    @Update("UPDATE budget_reservation SET status='RELEASED' WHERE id=#{id} AND status='RESERVED'")
    int releaseExpired(UUID id);
}
