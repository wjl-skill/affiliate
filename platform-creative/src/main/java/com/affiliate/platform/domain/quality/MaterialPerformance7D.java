package com.affiliate.platform.domain.quality;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 素材近 7 日投放事实指标 (Material 7-Day Performance Metrics)
 * <p>
 * 聚合自广告投放事实表，包含核心原始计数与派生比率指标。
 *
 * @param materialId       素材内部主键标识
 * @param creativeId       关联的 platform-creative 主实体 ID (可同 materialId)
 * @param materialName     素材内部命名
 * @param adName           投放渠道广告名称 (用于与三方渠道严格等值匹配)
 * @param statDate         统计截止日期
 * @param promotionCost    近 7 日总消耗金额 (USD 或 CNY)
 * @param impressions      近 7 日总曝光次数
 * @param clicks           近 7 日总点击次数
 * @param installNum       近 7 日总转化/安装次数
 * @param conversionValues 近 7 日总转化价值总额
 */
public record MaterialPerformance7D(
        String materialId,
        String creativeId,
        String materialName,
        String adName,
        LocalDate statDate,
        BigDecimal promotionCost,
        long impressions,
        long clicks,
        long installNum,
        BigDecimal conversionValues
) {
    public MaterialPerformance7D {
        promotionCost = promotionCost != null ? promotionCost : BigDecimal.ZERO;
        conversionValues = conversionValues != null ? conversionValues : BigDecimal.ZERO;
        impressions = Math.max(0, impressions);
        clicks = Math.max(0, clicks);
        installNum = Math.max(0, installNum);
        statDate = statDate != null ? statDate : LocalDate.now();
    }

    /**
     * 计算投资回报率 ROAS = conversionValues / promotionCost
     */
    public BigDecimal roas() {
        if (promotionCost == null || promotionCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return conversionValues.divide(promotionCost, 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算单次获客成本 CPI = promotionCost / installNum
     */
    public BigDecimal cpi() {
        if (installNum <= 0) {
            return promotionCost.compareTo(BigDecimal.ZERO) > 0 ? promotionCost : BigDecimal.ZERO;
        }
        return promotionCost.divide(BigDecimal.valueOf(installNum), 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算点击率 CTR = clicks / impressions
     */
    public BigDecimal ctr() {
        if (impressions <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(clicks).divide(BigDecimal.valueOf(impressions), 6, RoundingMode.HALF_UP);
    }

    /**
     * 计算转化率 CVR = installNum / clicks
     */
    public BigDecimal cvrInstall() {
        if (clicks <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(installNum).divide(BigDecimal.valueOf(clicks), 6, RoundingMode.HALF_UP);
    }
}
