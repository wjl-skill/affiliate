package com.affiliate.platform.integration;

import java.math.BigDecimal;

/**
 * 通用跨平台广告投放效果明细数据实体 (Normalized Ad Performance Record)
 *
 * @param platform     平台标识（如 GOOGLE_ADS, TIKTOK, FACEBOOK）
 * @param accountId    广告主客户账号 / Ad Account ID
 * @param campaignId   Campaign 广告系列唯一 ID
 * @param campaignName Campaign 名称
 * @param adGroupId    广告组 ID（可选）
 * @param adGroupName  广告组名称（可选）
 * @param impressions  曝光展示量
 * @param clicks       点击量
 * @param conversions  转化数
 * @param cost         广告消耗金额
 * @param currency     币种（默认 USD）
 * @param date         报表归属日期 (YYYY-MM-DD)
 */
public record AdPerformanceRecord(
        String platform,
        String accountId,
        String campaignId,
        String campaignName,
        String adGroupId,
        String adGroupName,
        long impressions,
        long clicks,
        long conversions,
        BigDecimal cost,
        String currency,
        String date
) {
    public AdPerformanceRecord(
            String platform,
            String accountId,
            String campaignId,
            String campaignName,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal cost,
            String date
    ) {
        this(platform, accountId, campaignId, campaignName, null, null,
                impressions, clicks, conversions, cost, "USD", date);
    }
}
