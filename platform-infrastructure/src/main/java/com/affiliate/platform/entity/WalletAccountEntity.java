package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 钱包账户持久化实体 (Wallet Account Entity)
 * <p>
 * 映射数据库表 `wallet_account`。
 */
@TableName("wallet_account")
public class WalletAccountEntity {

    @TableId(type = IdType.INPUT)
    private String accountId;
    private String tenantId;
    private BigDecimal cashBalance;
    private BigDecimal creditLimit;
    private BigDecimal frozenAmount;
    private String currency;
    private Instant updatedAt;

    public WalletAccountEntity() {}

    public WalletAccountEntity(String accountId, String tenantId, BigDecimal cashBalance,
                               BigDecimal creditLimit, BigDecimal frozenAmount, String currency,
                               Instant updatedAt) {
        this.accountId = accountId;
        this.tenantId = tenantId;
        this.cashBalance = cashBalance;
        this.creditLimit = creditLimit;
        this.frozenAmount = frozenAmount;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public BigDecimal getFrozenAmount() { return frozenAmount; }
    public void setFrozenAmount(BigDecimal frozenAmount) { this.frozenAmount = frozenAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
