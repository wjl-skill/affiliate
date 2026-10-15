package com.affiliate.platform.domain.ai.dashboard;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 视频使用量与算力成本大盘响应结构
 */
public record AiVideoUsageDashboardResponse(
        Overview overview,
        List<TrendRow> trend,
        List<DimensionRow> models,
        List<DimensionRow> users,
        List<DimensionRow> departments
) implements Serializable {

    public record Overview(
            long totalCount,
            long successCount,
            long failedCount,
            long processingCount,
            long canceledCount,
            long discardedCount,
            BigDecimal totalTokens,
            double totalOutputDurationSeconds,
            BigDecimal totalCost,
            String currency
    ) implements Serializable {}

    public record TrendRow(
            String statDate,
            long totalCount,
            long successCount,
            long failedCount,
            BigDecimal tokens,
            double outputDurationSeconds,
            BigDecimal cost
    ) implements Serializable {}

    public record DimensionRow(
            String dimensionKey,
            String dimensionName,
            long totalCount,
            long successCount,
            BigDecimal tokens,
            double outputDurationSeconds,
            BigDecimal cost
    ) implements Serializable {}
}
