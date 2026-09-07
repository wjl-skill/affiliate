package com.affiliate.platform.dsp;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 需求方智能出价策略与调价引擎 (Bidding Strategy & Pacing Calculator)
 * <p>
 * 支持业界主流出价模式：
 * 1. FIXED_CPM：千次展示固定出价；
 * 2. CPC：基于点击率预估出价 eCPM = pCTR * Target_CPC * 1000；
 * 3. OCPM / Target CPA：基于双重预估转化出价 eCPM = pCTR * pCVR * Target_CPA * 1000；
 * 4. Bid Shading：第一价拍卖环境下根据调价因子动态剪裁出价，避免胜者诅咒。
 */
public record BiddingStrategy(
        BiddingType type,
        BigDecimal targetPrice,
        BigDecimal maxBid,
        Double bidShadingFactor
) {
    public BiddingStrategy {
        if (targetPrice == null || targetPrice.signum() < 0) {
            throw new IllegalArgumentException("targetPrice must be non-negative");
        }
        if (maxBid == null || maxBid.signum() < 0) {
            throw new IllegalArgumentException("maxBid must be non-negative");
        }
        if (bidShadingFactor == null || bidShadingFactor <= 0.0 || bidShadingFactor > 1.0) {
            bidShadingFactor = 1.0; // 默认不打折
        }
    }

    public enum BiddingType {
        /** 千次展示固定出价 (Cost Per Mille) */
        FIXED_CPM,
        /** 按点击成本计费出价 (Cost Per Click) */
        CPC,
        /** 目标转化成本出价 (Cost Per Acquisition) */
        CPA,
        /** 智能优化千次展示出价 (Optimized CPM) */
        OCPM
    }

    /**
     * 根据转化漏斗预估概率计算最终参与 ADX 竞价的 eCPM 出价
     *
     * @param pCtr 预估点击率 (0.0 ~ 1.0)
     * @param pCvr 预估点击后转化率 (0.0 ~ 1.0)
     * @return 最终折算后的 eCPM 出价金额（含最高出价保护与 Bid Shading 剪裁）
     */
    public BigDecimal calculateEcpm(double pCtr, double pCvr) {
        BigDecimal rawEcpm;
        switch (type) {
            case FIXED_CPM -> rawEcpm = targetPrice;
            case CPC -> {
                // eCPM = pCTR * Target_CPC * 1000
                double val = Math.max(0.0001, pCtr) * targetPrice.doubleValue() * 1000.0;
                rawEcpm = BigDecimal.valueOf(val);
            }
            case CPA, OCPM -> {
                // eCPM = pCTR * pCVR * Target_CPA * 1000
                double val = Math.max(0.0001, pCtr) * Math.max(0.0001, pCvr) * targetPrice.doubleValue() * 1000.0;
                rawEcpm = BigDecimal.valueOf(val);
            }
            default -> rawEcpm = targetPrice;
        }

        // 应用出价保护上限 maxBid
        BigDecimal capped = rawEcpm.min(maxBid);

        // 应用 Bid Shading 降价因子
        BigDecimal finalBid = capped.multiply(BigDecimal.valueOf(bidShadingFactor));
        return finalBid.setScale(4, RoundingMode.HALF_UP);
    }
}
