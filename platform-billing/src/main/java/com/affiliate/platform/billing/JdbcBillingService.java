package com.affiliate.platform.billing;

import com.affiliate.platform.entity.BillingEntryEntity;
import com.affiliate.platform.mapper.BillingEntryMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 基于 MyBatis-Plus 的金融级计费记账持久化服务 (MyBatis-Plus Billing Service)
 * <p>
 * 对应数据库 `billing_entry` 表，使用 `BillingEntryMapper` 执行不可变流水追加与基于唯一幂等键防重。
 */
@Service
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class JdbcBillingService implements BillingService {

    private final BillingEntryMapper mapper;

    public JdbcBillingService(BillingEntryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public BillingEntry record(BillingEntry entry) {
        if (entry.amount() == null || entry.amount().signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (entry.idempotencyKey() == null || entry.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }

        // 1. 幂等校验：若幂等键已存在则直接返回历史已记录流水
        QueryWrapper<BillingEntryEntity> qw = new QueryWrapper<>();
        qw.eq("idempotency_key", entry.idempotencyKey());
        BillingEntryEntity existing = mapper.selectOne(qw);
        if (existing != null) {
            return toDomain(existing, entry.type(), entry.auctionId());
        }

        // 2. 生成全局唯一分录 UUID 与时间
        String entryId = entry.id() == null ? UUID.randomUUID().toString() : entry.id();
        Instant occurredAt = entry.occurredAt() == null ? Instant.now() : entry.occurredAt();
        String currency = (entry.currency() == null || entry.currency().isBlank()) ? "USD" : entry.currency().toUpperCase();
        String direction = entry.direction() == null ? EntryDirection.DEBIT.name() : entry.direction().name();

        // 3. 构造持久化实体并通过 MyBatis-Plus 插入
        BillingEntryEntity entity = new BillingEntryEntity(
                entryId,
                entry.tenantId(),
                entry.accountId(),
                direction,
                entry.amount(),
                currency,
                entry.description(),
                entry.idempotencyKey(),
                occurredAt
        );
        mapper.insert(entity);

        return new BillingEntry(
                entryId,
                entry.tenantId(),
                entry.accountId(),
                entry.auctionId(),
                entry.type(),
                EntryDirection.valueOf(direction),
                entry.amount(),
                currency,
                entry.idempotencyKey(),
                entry.description(),
                occurredAt
        );
    }

    @Override
    public List<BillingEntry> list(String tenantId) {
        QueryWrapper<BillingEntryEntity> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).orderByDesc("created_at").last("LIMIT 500");
        List<BillingEntryEntity> list = mapper.selectList(qw);

        return list.stream()
                .map(e -> toDomain(e, EntryType.ADVERTISER_CHARGE, null))
                .toList();
    }

    private BillingEntry toDomain(BillingEntryEntity e, EntryType fallbackType, String auctionId) {
        EntryDirection direction = EntryDirection.DEBIT;
        if (e.getDirection() != null) {
            try {
                direction = EntryDirection.valueOf(e.getDirection());
            } catch (Exception ignored) {}
        }

        return new BillingEntry(
                e.getId(),
                e.getTenantId(),
                e.getAccountId(),
                auctionId,
                fallbackType != null ? fallbackType : EntryType.ADVERTISER_CHARGE,
                direction,
                e.getAmount(),
                e.getCurrency() != null ? e.getCurrency().trim() : "USD",
                e.getIdempotencyKey(),
                e.getReason(),
                e.getCreatedAt()
        );
    }
}
