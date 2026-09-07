package com.affiliate.platform.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 广告报表衍生指标计算引擎 (Performance Metrics Calculator)
 * <p>
 * 严防除以零溢出，保障统计精度与数据鲁棒性。
 */
public final class PerformanceMetricsCalculator {

    private PerformanceMetricsCalculator() {}

    /**
     * 计算全套广告效果衍生指标
     *
     * @param impressions 展示曝光数
     * @param clicks      点击数
     * @param conversions 转化数
     * @param spend       广告主消耗总金额 (USD)
     * @param revenue     媒体结算分成总金额 (USD)
     * @return 衍生指标实体
     */
    public static DerivedMetrics calculate(
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            BigDecimal revenue
    ) {
        spend = spend == null ? BigDecimal.ZERO : spend;
        revenue = revenue == null ? BigDecimal.ZERO : revenue;

        // 1. CTR % = Clicks / Impressions * 100
        double ctr = impressions <= 0 ? 0.0 : ((double) clicks / impressions) * 100.0;
        ctr = round(ctr, 4);

        // 2. CVR % = Conversions / Clicks * 100
        double cvr = clicks <= 0 ? 0.0 : ((double) conversions / clicks) * 100.0;
        cvr = round(cvr, 4);

        // 3. eCPM = (Spend / Impressions) * 1000
        BigDecimal ecpm = impressions <= 0
                ? BigDecimal.ZERO
                : spend.multiply(BigDecimal.valueOf(1000.0)).divide(BigDecimal.valueOf(impressions), 4, RoundingMode.HALF_UP);

        // 4. eCPC = Spend / Clicks
        BigDecimal ecpc = clicks <= 0
                ? BigDecimal.ZERO
                : spend.divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP);

        // 5. CPA = Spend / Conversions
        BigDecimal cpa = conversions <= 0
                ? BigDecimal.ZERO
                : spend.divide(BigDecimal.valueOf(conversions), 4, RoundingMode.HALF_UP);

        // 6. 毛利 = Spend - Revenue
        BigDecimal grossProfit = spend.subtract(revenue).setScale(4, RoundingMode.HALF_UP);

        // 7. ROI % = (Revenue - Spend) / Spend * 100
        double roi = spend.signum() <= 0
                ? 0.0
                : grossProfit.divide(spend, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        roi = round(roi, 2);

        return new DerivedMetrics(ctr, cvr, ecpm, ecpc, cpa, grossProfit, roi);
    }

    private static double round(double val, int scale) {
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
