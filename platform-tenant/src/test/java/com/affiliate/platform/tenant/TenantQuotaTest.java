package com.affiliate.platform.tenant;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TenantQuotaTest {

    @Test
    void enforcesCampaignAndSpendLimits() {
        TenantQuotaService service = new TenantQuotaService();
        service.setQuota(new TenantQuota("t-vip", 10, 5, new BigDecimal("1000.00"), true));

        // 活动上限 10：当前 8 个允许激活，当前 10 个拒绝激活
        assertTrue(service.canActivateCampaign("t-vip", 8));
        assertFalse(service.canActivateCampaign("t-vip", 10));

        // 资金限额 1000：当前 800 + 新增 150 = 950 <= 1000 允许
        assertTrue(service.canSpend("t-vip", new BigDecimal("800.00"), new BigDecimal("150.00")));
        // 当前 800 + 新增 300 = 1100 > 1000 拒绝
        assertFalse(service.canSpend("t-vip", new BigDecimal("800.00"), new BigDecimal("300.00")));
    }

    @Test
    void enforcesQpsRateLimit() {
        TenantQuotaService service = new TenantQuotaService();
        // 设置每秒最多 3 次请求
        service.setQuota(new TenantQuota("t-qps", 10, 3, new BigDecimal("1000.00"), true));

        assertTrue(service.acquireQpsToken("t-qps"));
        assertTrue(service.acquireQpsToken("t-qps"));
        assertTrue(service.acquireQpsToken("t-qps"));

        // 第 4 次请求超限拒绝
        assertFalse(service.acquireQpsToken("t-qps"));
    }
}
