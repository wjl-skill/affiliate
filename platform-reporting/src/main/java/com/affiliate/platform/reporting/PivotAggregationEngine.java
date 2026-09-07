package com.affiliate.platform.reporting;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 广告报表多维透视聚合引擎 (Pivot Aggregation Engine)
 * <p>
 * 借鉴业界商业网盟与广告追踪系统（AppsFlyer、Adjust、Voluum、Everflow）：
 * 1. 支持任意多维动态切片与下钻分组（如 [date, offerId, affiliateId, country, device, sub1]）；
 * 2. 支持多维前置过滤筛选（维度精确过滤与通配匹配）；
 * 3. 自动计算全套衍生效果指标 (CTR, CVR, eCPM, eCPC, CPA, 毛利, ROI)；
 * 4. 支持任意指标的动态多维正逆向排序。
 */
@Service
public class PivotAggregationEngine {

    /**
     * 原始输入报表记录行
     */
    public record PivotRecord(
            Map<String, String> dimensions,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            BigDecimal revenue
    ) {
        public PivotRecord {
            dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
            spend = spend == null ? BigDecimal.ZERO : spend;
            revenue = revenue == null ? BigDecimal.ZERO : revenue;
        }
    }

    /**
     * 多维透视聚合统计结果行
     */
    public record PivotRow(
            Map<String, String> dimensions,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            BigDecimal revenue,
            DerivedMetrics metrics
    ) {}

    /**
     * 多维透视动态聚合与过滤排序
     *
     * @param records          原始明细记录集
     * @param groupByFields    下钻透视维度列表（如 ["offerId", "country"]）
     * @param dimensionFilters 维度前置过滤条件字典（如 {"country": "US"}）
     * @param sortByMetric     排序指标（如 "revenue", "conversions", "roi", "ctr", "impressions"）
     * @param descending       是否降序排列
     * @return 透视聚合后的结果列表
     */
    public List<PivotRow> aggregate(
            List<PivotRecord> records,
            List<String> groupByFields,
            Map<String, String> dimensionFilters,
            String sortByMetric,
            boolean descending
    ) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<String> effectiveGroups = (groupByFields == null || groupByFields.isEmpty())
                ? List.of("date")
                : groupByFields;

        // 1. 过滤流
        List<PivotRecord> filtered = records.stream().filter(r -> {
            if (dimensionFilters == null || dimensionFilters.isEmpty()) {
                return true;
            }
            for (Map.Entry<String, String> f : dimensionFilters.entrySet()) {
                String val = r.dimensions().get(f.getKey());
                if (val == null || !val.equalsIgnoreCase(f.getValue())) {
                    return false;
                }
            }
            return true;
        }).toList();

        // 2. 动态分组与累加
        Map<Map<String, String>, MutableMetricAccumulator> grouped = new LinkedHashMap<>();

        for (PivotRecord r : filtered) {
            Map<String, String> groupKey = new LinkedHashMap<>();
            for (String field : effectiveGroups) {
                groupKey.put(field, r.dimensions().getOrDefault(field, "UNKNOWN"));
            }

            grouped.computeIfAbsent(groupKey, k -> new MutableMetricAccumulator())
                    .accumulate(r.impressions(), r.clicks(), r.conversions(), r.spend(), r.revenue());
        }

        // 3. 构建结果行与衍生指标计算
        List<PivotRow> rows = new ArrayList<>(grouped.size());
        for (Map.Entry<Map<String, String>, MutableMetricAccumulator> entry : grouped.entrySet()) {
            MutableMetricAccumulator acc = entry.getValue();
            DerivedMetrics metrics = PerformanceMetricsCalculator.calculate(
                    acc.impressions,
                    acc.clicks,
                    acc.conversions,
                    acc.spend,
                    acc.revenue
            );

            rows.add(new PivotRow(
                    Map.copyOf(entry.getKey()),
                    acc.impressions,
                    acc.clicks,
                    acc.conversions,
                    acc.spend,
                    acc.revenue,
                    metrics
            ));
        }

        // 4. 排序
        Comparator<PivotRow> comparator = resolveComparator(sortByMetric);
        if (descending) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);

        return rows;
    }

    private Comparator<PivotRow> resolveComparator(String metric) {
        if (metric == null || metric.isBlank()) {
            return Comparator.comparing(PivotRow::revenue);
        }
        return switch (metric.toLowerCase(Locale.ROOT)) {
            case "impressions" -> Comparator.comparingLong(PivotRow::impressions);
            case "clicks" -> Comparator.comparingLong(PivotRow::clicks);
            case "conversions" -> Comparator.comparingLong(PivotRow::conversions);
            case "spend" -> Comparator.comparing(PivotRow::spend);
            case "ctr" -> Comparator.comparingDouble(r -> r.metrics().ctrPercent());
            case "cvr" -> Comparator.comparingDouble(r -> r.metrics().cvrPercent());
            case "ecpm" -> Comparator.comparing(r -> r.metrics().ecpm());
            case "cpa" -> Comparator.comparing(r -> r.metrics().cpa());
            case "profit", "grossprofit" -> Comparator.comparing(r -> r.metrics().grossProfit());
            case "roi" -> Comparator.comparingDouble(r -> r.metrics().roiPercent());
            default -> Comparator.comparing(PivotRow::revenue);
        };
    }

    private static class MutableMetricAccumulator {
        long impressions = 0;
        long clicks = 0;
        long conversions = 0;
        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;

        void accumulate(long imp, long clk, long conv, BigDecimal sp, BigDecimal rev) {
            impressions += imp;
            clicks += clk;
            conversions += conv;
            spend = spend.add(sp);
            revenue = revenue.add(rev);
        }
    }
}
