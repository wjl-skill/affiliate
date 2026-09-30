package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.SubIdStatsEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * Sub-ID 统计数据访问层 Mapper (Sub-ID Stats Mapper)
 */
@Mapper
public interface SubIdStatsMapper extends BaseMapper<SubIdStatsEntity> {

    /**
     * PostgreSQL 原生原子增量插入或累加合并 (Atomic Upsert Incremental)
     * <p>
     * 基于复合主键 (tenant_id, affiliate_id, sub1) 实施行锁级别原子累加与实时 EPC/CR% 计算，
     * 彻底杜绝并发点击与转化统计时的 Lost Update 更新丢失问题。
     */
    @Insert("""
        INSERT INTO affiliate_sub_id_stats (tenant_id, affiliate_id, sub1, clicks, conversions, total_payout, total_revenue, epc, cr_percent, updated_at)
        VALUES (#{tenantId}, #{affiliateId}, #{sub1}, #{clicks}, #{conversions}, #{totalPayout}, #{totalRevenue}, #{epc}, #{crPercent}, #{updatedAt})
        ON CONFLICT (tenant_id, affiliate_id, sub1)
        DO UPDATE SET
            clicks = affiliate_sub_id_stats.clicks + EXCLUDED.clicks,
            conversions = affiliate_sub_id_stats.conversions + EXCLUDED.conversions,
            total_payout = affiliate_sub_id_stats.total_payout + EXCLUDED.total_payout,
            total_revenue = affiliate_sub_id_stats.total_revenue + EXCLUDED.total_revenue,
            epc = CASE
                WHEN (affiliate_sub_id_stats.clicks + EXCLUDED.clicks) > 0
                THEN ROUND((affiliate_sub_id_stats.total_payout + EXCLUDED.total_payout) / (affiliate_sub_id_stats.clicks + EXCLUDED.clicks), 4)
                ELSE 0
            END,
            cr_percent = CASE
                WHEN (affiliate_sub_id_stats.clicks + EXCLUDED.clicks) > 0
                THEN ROUND(((affiliate_sub_id_stats.conversions + EXCLUDED.conversions)::numeric / (affiliate_sub_id_stats.clicks + EXCLUDED.clicks)) * 100, 2)
                ELSE 0
            END,
            updated_at = EXCLUDED.updated_at
    """)
    int upsertIncremental(SubIdStatsEntity entity);
}

