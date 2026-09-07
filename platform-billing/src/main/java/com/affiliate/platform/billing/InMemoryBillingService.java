package com.affiliate.platform.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于本地内存并发 Map 的计费服务实现 (In-Memory Billing Service)
 * <p>
 * 在未配置或关闭 PostgreSQL 数据库时作为默认回退实现，支持幂等键去重与租户多维度查询。
 */
@Service
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryBillingService implements BillingService {

    // 内存账本分录存储容器：Key 为 idempotencyKey（幂等键），Value 为已记账的分录对象
    private final ConcurrentMap<String, BillingEntry> entries = new ConcurrentHashMap<>();

    /**
     * 记录一笔计费分录
     *
     * @param entry 待入账分录
     * @return 实际保存的分录实体
     */
    @Override
    public BillingEntry record(BillingEntry entry) {
        if (entry.amount() == null || entry.amount().signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (entry.idempotencyKey() == null || entry.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }

        // 使用 computeIfAbsent 基于幂等键原子去重：若已存在则直接返回历史对象，不产生二次扣减
        return entries.computeIfAbsent(entry.idempotencyKey(), ignored -> new BillingEntry(
                entry.id() == null ? UUID.randomUUID().toString() : entry.id(),
                entry.tenantId(),
                entry.accountId(),
                entry.auctionId(),
                entry.type(),
                entry.direction() == null ? EntryDirection.DEBIT : entry.direction(),
                entry.amount(),
                entry.currency() == null ? "USD" : entry.currency(),
                entry.idempotencyKey(),
                entry.description(),
                entry.occurredAt() == null ? Instant.now() : entry.occurredAt()
        ));
    }

    /**
     * 根据租户标识查询计费明细
     *
     * @param tenantId 租户唯一标识
     * @return 属于该租户的分录列表
     */
    @Override
    public List<BillingEntry> list(String tenantId) {
        return entries.values().stream()
                .filter(e -> e.tenantId().equals(tenantId))
                .toList();
    }
}
