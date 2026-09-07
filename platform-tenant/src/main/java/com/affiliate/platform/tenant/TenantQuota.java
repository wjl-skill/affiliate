package com.affiliate.platform.tenant;

import java.math.BigDecimal;

/**
 * 多租户资源配额与服务等级 (SLA) 策略实体 (Tenant Quota Record)
 * <p>
 * 严控租户滥用系统资源，实现企业级租户隔离与背压限流：
 * 1. maxActiveCampaigns：最大允许并发投放的活跃活动数；
 * 2. maxQps：单位秒级别最大允许调用的竞价撮合或 API 吞吐量；
 * 3. maxDailySpend：租户单日投放资金安全红线上限。
 */
public record TenantQuota(
        String tenantId,
        int maxActiveCampaigns,
        int maxQps,
        BigDecimal maxDailySpend,
        boolean active
) {
    public TenantQuota {
        if (maxActiveCampaigns <= 0) maxActiveCampaigns = 100;
        if (maxQps <= 0) maxQps = 1000;
        if (maxDailySpend == null || maxDailySpend.signum() <= 0) {
            maxDailySpend = new BigDecimal("100000.00");
        }
    }

    public static TenantQuota standard(String tenantId) {
        return new TenantQuota(tenantId, 50, 500, new BigDecimal("50000.00"), true);
    }
}
