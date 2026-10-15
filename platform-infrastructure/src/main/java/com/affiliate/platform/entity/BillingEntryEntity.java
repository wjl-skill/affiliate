package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 资金账目流水持久化实体 (BillingEntry MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `billing_entry`。
 */
@TableName("billing_entry")
public class BillingEntryEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String accountId;
    private String auctionId;
    private String entryType;
    private String direction;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String idempotencyKey;
    private Instant occurredAt;

    public BillingEntryEntity() {}

    public BillingEntryEntity(String id, String tenantId, String accountId, String auctionId,
                              String entryType, String direction, BigDecimal amount, String currency,
                              String description, String idempotencyKey, Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.auctionId = auctionId;
        this.entryType = entryType;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }

    public BillingEntryEntity(String id, String tenantId, String accountId, String direction,
                              BigDecimal amount, String currency, String reason, String idempotencyKey, Instant createdAt) {
        this(id, tenantId, accountId, null, "ADVERTISER_CHARGE", direction, amount, currency, reason, idempotencyKey, createdAt);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReason() { return description; }
    public void setReason(String reason) { this.description = reason; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Instant getCreatedAt() { return occurredAt; }
    public void setCreatedAt(Instant createdAt) { this.occurredAt = createdAt; }
}
