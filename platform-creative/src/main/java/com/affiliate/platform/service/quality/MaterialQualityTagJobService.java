package com.affiliate.platform.service.quality;

import com.affiliate.platform.domain.quality.MaterialPerformance7D;
import com.affiliate.platform.domain.quality.MaterialQualityResult;

import java.time.LocalDate;
import java.util.List;

/**
 * 素材质量自动评估作业服务端口 (Material Quality Tag Job Service Port)
 * <p>
 * 负责单素材触发刷新、租户全盘素材批量重算以及每日定时任务执行。
 */
public interface MaterialQualityTagJobService {

    /**
     * 单素材立即刷新重算
     */
    MaterialQualityResult refreshSingle(String tenantId, String creativeId, LocalDate statDate, MaterialPerformance7D performance);

    /**
     * 批量刷新指定租户下多素材的质量标签
     */
    JobRefreshSummary batchRefresh(String tenantId, LocalDate statDate, List<MaterialPerformance7D> performances);

    /**
     * 定时调度执行全量刷新 (可挂载至 Cron 或 XXL-Job)
     */
    JobRefreshSummary executeDailyScheduledRefresh(String tenantId);

    /**
     * 批量刷新作业汇总结果
     */
    record JobRefreshSummary(
            String tenantId,
            LocalDate statDate,
            int totalProcessed,
            int starCount,
            int potentialCount,
            int coreCount,
            int riskCount,
            int blackholeCount,
            int coldStartCount,
            long executionTimeMillis
    ) {}
}
