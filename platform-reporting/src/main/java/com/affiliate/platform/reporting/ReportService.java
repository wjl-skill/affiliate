package com.affiliate.platform.reporting;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.entity.ReportDailyEntity;
import com.affiliate.platform.mapper.ReportDailyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 广告报表多维聚合统计服务 (Reporting OLAP Aggregation Service)
 * <p>
 * 负责按租户、日期、活动等多维度实时累计广告曝光量、点击量、转化量、广告消耗与平台营收，
 * 并提供全套核心衍生指标（CTR、CVR、eCPM、eCPC、CPA、毛利与 ROI）的实时计算。
 * 存储层基于 MyBatis-Plus `ReportDailyMapper` 执行聚合持久化，读取层通过 Guava + Redis 两级缓存加速。
 */
@Service
public class ReportService {

    private final ReportDailyMapper reportMapper;
    private final TwoTierCache<String, DailyReport> cache;

    // 内存降级容器（在未装配数据库时供本地单机模式使用）
    private final Map<String, DailyReport> localReports = new ConcurrentHashMap<>();

    @Autowired
    public ReportService(
            @Autowired(required = false) ReportDailyMapper reportMapper,
            @Autowired(required = false) TwoTierCacheManager cacheManager
    ) {
        this.reportMapper = reportMapper;
        this.cache = cacheManager != null ? cacheManager.getOrCreate("report_daily", DailyReport.class) : null;
    }

    public ReportService() {
        this(null, null);
    }

    /**
     * 增量原子累加一笔业务效果与资金流水
     */
    public DailyReport record(
            String tenantId,
            LocalDate date,
            String campaignId,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            BigDecimal revenue
    ) {
        String key = tenantId + ":" + date + ":" + campaignId;

        if (reportMapper != null) {
            QueryWrapper<ReportDailyEntity> qw = new QueryWrapper<>();
            qw.eq("tenant_id", tenantId).eq("report_date", date).eq("campaign_id", campaignId);
            ReportDailyEntity existing = reportMapper.selectOne(qw);

            DailyReport result;
            if (existing == null) {
                ReportDailyEntity entity = new ReportDailyEntity(
                        tenantId, date, campaignId,
                        impressions, clicks, conversions,
                        spend, revenue
                );
                reportMapper.insert(entity);
                result = new DailyReport(tenantId, date, campaignId, impressions, clicks, conversions, spend, revenue);
            } else {
                long nextImp = existing.getImpressions() + impressions;
                long nextClk = existing.getClicks() + clicks;
                long nextConv = existing.getConversions() + conversions;
                BigDecimal nextSpend = existing.getSpend().add(spend);
                BigDecimal nextRevenue = existing.getRevenue().add(revenue);

                ReportDailyEntity updateEntity = new ReportDailyEntity(
                        tenantId, date, campaignId,
                        nextImp, nextClk, nextConv,
                        nextSpend, nextRevenue
                );

                UpdateWrapper<ReportDailyEntity> uw = new UpdateWrapper<>();
                uw.eq("tenant_id", tenantId).eq("report_date", date).eq("campaign_id", campaignId);
                reportMapper.update(updateEntity, uw);

                result = new DailyReport(tenantId, date, campaignId, nextImp, nextClk, nextConv, nextSpend, nextRevenue);
            }

            if (cache != null) {
                cache.put(key, result);
            }
            return result;
        }

        // 内存模式降级处理
        DailyReport report = localReports.compute(key, (k, old) -> {
            if (old == null) {
                return new DailyReport(tenantId, date, campaignId, impressions, clicks, conversions, spend, revenue);
            }
            return old.add(impressions, clicks, conversions, spend, revenue);
        });

        if (cache != null) {
            cache.put(key, report);
        }
        return report;
    }

    /**
     * 按租户与日期范围查询日报表汇总列表
     */
    public List<DailyReport> list(String tenantId, LocalDate from, LocalDate to) {
        if (reportMapper != null) {
            QueryWrapper<ReportDailyEntity> qw = new QueryWrapper<>();
            qw.eq("tenant_id", tenantId)
                    .ge("report_date", from)
                    .le("report_date", to)
                    .orderByDesc("report_date");

            List<ReportDailyEntity> entities = reportMapper.selectList(qw);
            List<DailyReport> list = new ArrayList<>(entities.size());
            for (ReportDailyEntity e : entities) {
                list.add(new DailyReport(
                        e.getTenantId(),
                        e.getReportDate(),
                        e.getCampaignId(),
                        e.getImpressions(),
                        e.getClicks(),
                        e.getConversions(),
                        e.getSpend(),
                        e.getRevenue()
                ));
            }
            return list;
        }

        return localReports.values().stream()
                .filter(r -> r.tenantId().equals(tenantId) && !r.date().isBefore(from) && !r.date().isAfter(to))
                .toList();
    }

    /**
     * 自动计算指定日报表记录的衍生效果指标
     */
    public DerivedMetrics getMetrics(DailyReport report) {
        if (report == null) {
            return PerformanceMetricsCalculator.calculate(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return PerformanceMetricsCalculator.calculate(
                report.impressions(),
                report.clicks(),
                report.conversions(),
                report.spend(),
                report.revenue()
        );
    }

    /**
     * 日报表多维聚合统计记录实体 (Daily Report Record)
     */
    public record DailyReport(
            String tenantId,
            LocalDate date,
            String campaignId,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            BigDecimal revenue
    ) {
        DailyReport add(long i, long c, long cv, BigDecimal s, BigDecimal r) {
            return new DailyReport(
                    tenantId,
                    date,
                    campaignId,
                    impressions + i,
                    clicks + c,
                    conversions + cv,
                    spend.add(s),
                    revenue.add(r)
            );
        }

        public DerivedMetrics toMetrics() {
            return PerformanceMetricsCalculator.calculate(impressions, clicks, conversions, spend, revenue);
        }
    }
}
