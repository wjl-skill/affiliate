package com.affiliate.platform.integration;

/**
 * 跨平台广告投放效果报表检索请求参数载荷
 *
 * @param accountId   广告主客户账号 ID
 * @param accessToken 授权访问凭据
 * @param startDate   开始日期 (YYYY-MM-DD)
 * @param endDate     结束日期 (YYYY-MM-DD)
 * @param level       聚合层级 (CAMPAIGN, ADGROUP, AD)
 */
public record AdPerformanceQuery(
        String accountId,
        String accessToken,
        String startDate,
        String endDate,
        String level
) {
    public AdPerformanceQuery(String accountId, String accessToken, String date) {
        this(accountId, accessToken, date, date, "CAMPAIGN");
    }
}
