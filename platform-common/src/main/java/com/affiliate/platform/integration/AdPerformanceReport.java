package com.affiliate.platform.integration;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨平台投放指标聚合报表结果集
 *
 * @param platform         平台标识
 * @param accountId        广告主账号 ID
 * @param records          明细记录列表
 * @param totalCost        总消耗金额
 * @param totalImpressions 总曝光量
 * @param totalClicks      总点击量
 * @param totalConversions 总转化数
 */
public record AdPerformanceReport(
        String platform,
        String accountId,
        List<AdPerformanceRecord> records,
        BigDecimal totalCost,
        long totalImpressions,
        long totalClicks,
        long totalConversions
) {
    public static AdPerformanceReport of(String platform, String accountId, List<AdPerformanceRecord> records) {
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalImpressions = 0L;
        long totalClicks = 0L;
        long totalConversions = 0L;

        if (records != null) {
            for (AdPerformanceRecord r : records) {
                if (r.cost() != null) {
                    totalCost = totalCost.add(r.cost());
                }
                totalImpressions += r.impressions();
                totalClicks += r.clicks();
                totalConversions += r.conversions();
            }
        }

        return new AdPerformanceReport(
                platform,
                accountId,
                records != null ? records : List.of(),
                totalCost,
                totalImpressions,
                totalClicks,
                totalConversions
        );
    }
}
