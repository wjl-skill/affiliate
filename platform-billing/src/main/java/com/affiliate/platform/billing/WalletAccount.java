package com.affiliate.platform.billing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 广告主/媒体钱包账户领域实体 (Wallet Account Domain Record)
 * <p>
 * 维护商户在平台的实时资金状态：
 * 包含现金充值余额、后付费授信额度（Credit Limit）及竞价交易中冻结金额（Frozen）。
 */
public record WalletAccount(
        String accountId,
        String tenantId,
        BigDecimal cashBalance,
        BigDecimal creditLimit,
        BigDecimal frozenAmount,
        String currency,
        Instant updatedAt
) {
    public WalletAccount {
        if (cashBalance == null) cashBalance = BigDecimal.ZERO;
        if (creditLimit == null || creditLimit.signum() < 0) creditLimit = BigDecimal.ZERO;
        if (frozenAmount == null || frozenAmount.signum() < 0) frozenAmount = BigDecimal.ZERO;
        if (currency == null || currency.isBlank()) currency = "USD";
        if (updatedAt == null) updatedAt = Instant.now();
    }

    /**
     * 计算当前可用于竞价消耗的净可用余额 (Net Available Balance)
     * 可用额度 = 现金余额 + 授信额度 - 当前预占冻结金额
     */
    public BigDecimal availableBalance() {
        return cashBalance.add(creditLimit).subtract(frozenAmount);
    }

    /**
     * 判断是否拥有足够余额执行指定额度的预占
     */
    public boolean canHold(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return false;
        }
        return availableBalance().compareTo(amount) >= 0;
    }
}
