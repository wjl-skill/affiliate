package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.entity.SubIdStatsEntity;
import com.affiliate.platform.mapper.SubIdStatsMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 渠道 Sub-ID 级流式统计与实时 EPC 分析服务 (Sub-ID Analytics & EPC Engine - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL `affiliate_sub_id_stats` 表。
 */
@Service
public class SubIdAnalyticsService {

    private final SubIdStatsMapper statsMapper;
    private final ConcurrentMap<String, SubIdMetricBucket> fallbackMetrics = new ConcurrentHashMap<>();

    public SubIdAnalyticsService() {
        this(null);
    }

    @Autowired
    public SubIdAnalyticsService(@Autowired(required = false) SubIdStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public void recordClick(String affiliateId, String sub1) {
        String affId = affiliateId != null ? affiliateId : "all";
        String s1 = sub1 != null ? sub1 : "default";

        if (statsMapper != null) {
            QueryWrapper<SubIdStatsEntity> qw = new QueryWrapper<>();
            qw.eq("affiliate_id", affId).eq("sub1", s1);
            SubIdStatsEntity entity = statsMapper.selectOne(qw);

            if (entity == null) {
                entity = new SubIdStatsEntity("public", affId, s1, 1L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());
                statsMapper.insert(entity);
            } else {
                entity.setClicks(entity.getClicks() + 1);
                recomputeMetrics(entity);
                statsMapper.update(entity, qw);
            }
            return;
        }

        String key = key(affId, s1);
        fallbackMetrics.computeIfAbsent(key, k -> new SubIdMetricBucket()).clicks.incrementAndGet();
    }

    public void recordConversion(String affiliateId, String sub1, BigDecimal payout, BigDecimal revenue) {
        String affId = affiliateId != null ? affiliateId : "all";
        String s1 = sub1 != null ? sub1 : "default";
        BigDecimal p = payout != null ? payout : BigDecimal.ZERO;
        BigDecimal r = revenue != null ? revenue : BigDecimal.ZERO;

        if (statsMapper != null) {
            QueryWrapper<SubIdStatsEntity> qw = new QueryWrapper<>();
            qw.eq("affiliate_id", affId).eq("sub1", s1);
            SubIdStatsEntity entity = statsMapper.selectOne(qw);

            if (entity == null) {
                entity = new SubIdStatsEntity("public", affId, s1, 1L, 1L, p, r, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());
                recomputeMetrics(entity);
                statsMapper.insert(entity);
            } else {
                entity.setConversions(entity.getConversions() + 1);
                entity.setTotalPayout(entity.getTotalPayout().add(p));
                entity.setTotalRevenue(entity.getTotalRevenue().add(r));
                recomputeMetrics(entity);
                statsMapper.update(entity, qw);
            }
            return;
        }

        String key = key(affId, s1);
        SubIdMetricBucket bucket = fallbackMetrics.computeIfAbsent(key, k -> new SubIdMetricBucket());
        bucket.conversions.incrementAndGet();
        bucket.addPayout(p);
        bucket.addRevenue(r);
    }

    public SubIdPerformance getPerformance(String affiliateId, String sub1) {
        String affId = affiliateId != null ? affiliateId : "all";
        String s1 = sub1 != null ? sub1 : "default";

        if (statsMapper != null) {
            QueryWrapper<SubIdStatsEntity> qw = new QueryWrapper<>();
            qw.eq("affiliate_id", affId).eq("sub1", s1);
            SubIdStatsEntity entity = statsMapper.selectOne(qw);

            if (entity == null) {
                return new SubIdPerformance(affId, s1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            long clicks = entity.getClicks();
            long conversions = entity.getConversions();
            BigDecimal payout = entity.getTotalPayout();
            BigDecimal revenue = entity.getTotalRevenue();
            double cr = entity.getCrPercent() != null ? entity.getCrPercent().doubleValue() : 0.0;
            BigDecimal epc = entity.getEpc() != null ? entity.getEpc() : BigDecimal.ZERO;
            BigDecimal rpc = clicks <= 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP);
            BigDecimal margin = revenue.subtract(payout).setScale(4, RoundingMode.HALF_UP);

            return new SubIdPerformance(affId, s1, clicks, conversions, payout, revenue, epc, cr, rpc, margin);
        }

        String key = key(affId, s1);
        SubIdMetricBucket bucket = fallbackMetrics.get(key);
        if (bucket == null) {
            return new SubIdPerformance(affId, s1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        long clicks = bucket.clicks.get();
        long conversions = bucket.conversions.get();
        BigDecimal payout = bucket.getPayout();
        BigDecimal revenue = bucket.getRevenue();

        double cr = clicks <= 0 ? 0.0 : ((double) conversions / clicks) * 100.0;
        cr = BigDecimal.valueOf(cr).setScale(2, RoundingMode.HALF_UP).doubleValue();

        BigDecimal epc = clicks <= 0 ? BigDecimal.ZERO : payout.divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP);
        BigDecimal rpc = clicks <= 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP);
        BigDecimal margin = revenue.subtract(payout).setScale(4, RoundingMode.HALF_UP);

        return new SubIdPerformance(affId, s1, clicks, conversions, payout, revenue, epc, cr, rpc, margin);
    }

    private void recomputeMetrics(SubIdStatsEntity entity) {
        long c = entity.getClicks();
        long conv = entity.getConversions();
        BigDecimal payout = entity.getTotalPayout();

        if (c > 0) {
            double cr = ((double) conv / c) * 100.0;
            entity.setCrPercent(BigDecimal.valueOf(cr).setScale(2, RoundingMode.HALF_UP));
            entity.setEpc(payout.divide(BigDecimal.valueOf(c), 4, RoundingMode.HALF_UP));
        } else {
            entity.setCrPercent(BigDecimal.ZERO);
            entity.setEpc(BigDecimal.ZERO);
        }
        entity.setUpdatedAt(Instant.now());
    }

    private static String key(String affiliateId, String sub1) {
        return (affiliateId == null ? "all" : affiliateId) + ":" + (sub1 == null ? "default" : sub1);
    }

    public record SubIdPerformance(
            String affiliateId,
            String sub1,
            long clicks,
            long conversions,
            BigDecimal totalPayout,
            BigDecimal totalRevenue,
            BigDecimal epc,
            double crPercent,
            BigDecimal rpc,
            BigDecimal margin
    ) {}

    private static class SubIdMetricBucket {
        final AtomicLong clicks = new AtomicLong(0);
        final AtomicLong conversions = new AtomicLong(0);
        private BigDecimal payout = BigDecimal.ZERO;
        private BigDecimal revenue = BigDecimal.ZERO;

        synchronized void addPayout(BigDecimal p) { payout = payout.add(p); }
        synchronized void addRevenue(BigDecimal r) { revenue = revenue.add(r); }
        synchronized BigDecimal getPayout() { return payout; }
        synchronized BigDecimal getRevenue() { return revenue; }
    }
}
