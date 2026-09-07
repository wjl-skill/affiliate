package com.affiliate.platform.affiliate.domain;

import java.math.BigDecimal;

/**
 * 渠道专属阶梯出价规则 (Offer Tier Payout Rule)
 * <p>
 * 允许网盟运营为重点优质渠道（如 VIP 渠道客）在特定 Offer 上配置专属高于基准的佣金率。
 */
public record OfferTierPayout(
        String offerId,
        String affiliateId,
        AffiliatePartner.Tier targetTier,
        BigDecimal customPayout,
        BigDecimal customRevenue
) {}
