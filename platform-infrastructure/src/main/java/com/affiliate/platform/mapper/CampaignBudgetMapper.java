package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.CampaignBudgetEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.Instant;

@Mapper
public interface CampaignBudgetMapper extends BaseMapper<CampaignBudgetEntity> {
    @Insert("INSERT INTO campaign_budget (id, tenant_id, campaign_id, total_budget, daily_budget, balance, updated_at) VALUES (#{id}, #{tenantId}, #{campaignId}, #{totalBudget}, #{dailyBudget}, #{balance}, #{updatedAt}) ON CONFLICT (tenant_id, campaign_id) DO UPDATE SET total_budget=EXCLUDED.total_budget, daily_budget=EXCLUDED.daily_budget, balance=EXCLUDED.balance, updated_at=EXCLUDED.updated_at")
    int upsert(CampaignBudgetEntity entity);

    @Update("UPDATE campaign_budget SET balance = balance - #{amount}, updated_at = #{updatedAt} WHERE tenant_id = #{tenantId} AND campaign_id = #{campaignId} AND balance >= #{amount}")
    int reserveBalance(String tenantId, String campaignId, BigDecimal amount, Instant updatedAt);

    @Update("UPDATE campaign_budget SET balance = balance + #{amount}, updated_at = #{updatedAt} WHERE tenant_id = #{tenantId} AND campaign_id = #{campaignId}")
    int releaseBalance(String tenantId, String campaignId, BigDecimal amount, Instant updatedAt);

    @Select("SELECT balance FROM campaign_budget WHERE tenant_id = #{tenantId} AND campaign_id = #{campaignId}")
    BigDecimal selectBalance(String tenantId, String campaignId);
}
