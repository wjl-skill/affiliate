package com.affiliate.platform.tenant;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 租户配额管控与高并发速率限制服务 (Tenant Quota & Rate Limiter Service)
 * <p>
 * 提供秒级滑动窗口限流与业务硬配额检查，防止单租户打崩底层集群。
 */
@Service
public class TenantQuotaService {

    private final ConcurrentMap<String, TenantQuota> quotas = new ConcurrentHashMap<>();

    // QPS 计数器：Key 为 "tenantId:epochSecond"
    private final ConcurrentMap<String, AtomicInteger> qpsCounters = new ConcurrentHashMap<>();

    public void setQuota(TenantQuota quota) {
        quotas.put(quota.tenantId(), quota);
    }

    public TenantQuota getQuota(String tenantId) {
        return quotas.computeIfAbsent(tenantId, TenantQuota::standard);
    }

    /**
     * 校验租户是否允许创建/激活新活动
     */
    public boolean canActivateCampaign(String tenantId, int currentActiveCount) {
        TenantQuota q = getQuota(tenantId);
        return q.active() && currentActiveCount < q.maxActiveCampaigns();
    }

    /**
     * 秒级 QPS 限流令牌获取 (Token Acquisition)
     *
     * @return true 代表放行，false 代表触发租户限流拒绝
     */
    public boolean acquireQpsToken(String tenantId) {
        TenantQuota q = getQuota(tenantId);
        if (!q.active()) {
            return false;
        }

        long currentSec = Instant.now().getEpochSecond();
        String counterKey = tenantId + ":" + currentSec;

        AtomicInteger counter = qpsCounters.computeIfAbsent(counterKey, k -> new AtomicInteger(0));
        int currentRequests = counter.incrementAndGet();

        // 超过配额 QPS 时阻断
        return currentRequests <= q.maxQps();
    }

    /**
     * 校验租户当日累计消耗是否触及安全红线
     */
    public boolean canSpend(String tenantId, BigDecimal currentDailySpend, BigDecimal proposedSpend) {
        TenantQuota q = getQuota(tenantId);
        if (!q.active()) {
            return false;
        }
        BigDecimal total = (currentDailySpend == null ? BigDecimal.ZERO : currentDailySpend)
                .add(proposedSpend == null ? BigDecimal.ZERO : proposedSpend);
        return total.compareTo(q.maxDailySpend()) <= 0;
    }
}
