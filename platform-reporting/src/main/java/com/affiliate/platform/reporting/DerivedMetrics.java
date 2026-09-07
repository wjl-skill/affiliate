package com.affiliate.platform.reporting;

import java.math.BigDecimal;

/**
 * 广告商业智能衍生绩效指标实体 (Derived Performance Metrics)
 * <p>
 * 封装工业界评估广告效果与媒体收益的核心衍生指标：
 * 1. CTR (点击率 %): Clicks / Impressions * 100
 * 2. CVR (转化率 %): Conversions / Clicks * 100
 * 3. eCPM (千次展示成本/收益): (Spend / Impressions) * 1000
 * 4. eCPC (单次点击成本): Spend / Clicks
 * 5. CPA (单次转化成本): Spend / Conversions
 * 6. 毛利润 (Gross Profit): Spend - Revenue
 * 7. ROI (投资回报率 %): (Revenue - Spend) / Spend * 100
 */
public record DerivedMetrics(
        double ctrPercent,
        double cvrPercent,
        BigDecimal ecpm,
        BigDecimal ecpc,
        BigDecimal cpa,
        BigDecimal grossProfit,
        double roiPercent
) {}
