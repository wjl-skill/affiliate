package com.affiliate.platform.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 媒体流量分成与平台佣金抽成结算服务 (Revenue Share & Payout Service)
 * <p>
 * 规则：
 * 平台服务费 = 广告消耗总额 * 平台抽成比例；
 * 媒体结算收益 = 广告消耗总额 - 平台服务费。
 */
public class RevenueShareService {

    public record PayoutSplit(
            BigDecimal grossSpend,
            BigDecimal platformFee,
            BigDecimal publisherPayout,
            double platformFeeRate
    ) {}

    /**
     * 计算单笔或聚合广告消耗的分成拆解
     *
     * @param grossSpend      广告消耗总额 (USD)
     * @param platformFeeRate 平台抽成比例 (如 0.15 代表 15%)
     * @return 分成明细结果
     */
    public PayoutSplit split(BigDecimal grossSpend, double platformFeeRate) {
        if (grossSpend == null || grossSpend.signum() <= 0) {
            return new PayoutSplit(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, platformFeeRate);
        }

        double rate = Math.max(0.0, Math.min(1.0, platformFeeRate));
        BigDecimal fee = grossSpend.multiply(BigDecimal.valueOf(rate)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal payout = grossSpend.subtract(fee).setScale(4, RoundingMode.HALF_UP);

        return new PayoutSplit(grossSpend, fee, payout, rate);
    }
}
