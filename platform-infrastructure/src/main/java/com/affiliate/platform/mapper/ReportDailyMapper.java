package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.ReportDailyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 广告效果日报表聚合数据访问层 Mapper (ReportDaily Mapper)
 */
@Mapper
public interface ReportDailyMapper extends BaseMapper<ReportDailyEntity> {

    /**
     * PostgreSQL 原生原子增量插入或累加合并 (Atomic Upsert Incremental)
     * <p>
     * 基于唯一主键 (tenant_id, report_date, campaign_id) 实施行锁级别原子累加，
     * 彻底杜绝并发曝光与点击统计时的 Lost Update 更新丢失问题。
     */
    @Insert("""
        INSERT INTO report_daily (tenant_id, report_date, campaign_id, impressions, clicks, conversions, spend, revenue)
        VALUES (#{tenantId}, #{reportDate}, #{campaignId}, #{impressions}, #{clicks}, #{conversions}, #{spend}, #{revenue})
        ON CONFLICT (tenant_id, report_date, campaign_id)
        DO UPDATE SET
            impressions = report_daily.impressions + EXCLUDED.impressions,
            clicks = report_daily.clicks + EXCLUDED.clicks,
            conversions = report_daily.conversions + EXCLUDED.conversions,
            spend = report_daily.spend + EXCLUDED.spend,
            revenue = report_daily.revenue + EXCLUDED.revenue
    """)
    int upsertIncremental(ReportDailyEntity entity);
}
