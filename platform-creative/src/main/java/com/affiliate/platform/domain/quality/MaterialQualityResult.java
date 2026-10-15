package com.affiliate.platform.domain.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 素材质量评估结果领域实体 (Material Quality Evaluation Result)
 * <p>
 * 封装单次评级结论、综合打分、命中规则细则、建议策略与指标快照。
 *
 * @param id           评估记录主键 ID
 * @param tenantId     租户标识
 * @param creativeId   关联素材 ID
 * @param materialName 素材展示名称
 * @param statDate     评估基准日期
 * @param qualityLevel 最终判定的质量等级 (UNRATED, STAR, POTENTIAL, CORE, RISK, BLACKHOLE)
 * @param sampleStatus 样本有效性状态 (SUFFICIENT, INSUFFICIENT, UNMATCHED)
 * @param score        综合评分 (0~100)
 * @param reasonTags   命中的业务原因标签列表 (如 "ROAS_ABOVE_P75", "CPI_BELOW_AVG")
 * @param ruleHits     底层规则触发命中明细
 * @param actionAdvice 系统推荐的投放操作建议
 * @param metrics      评估时采样的素材 7 日指标快照
 * @param benchmark    评估时对比的大盘基准线快照
 * @param evaluatedAt  评估执行完成时间戳
 */
public record MaterialQualityResult(
        String id,
        String tenantId,
        String creativeId,
        String materialName,
        LocalDate statDate,
        MaterialQualityLevel qualityLevel,
        MaterialSampleStatus sampleStatus,
        BigDecimal score,
        List<String> reasonTags,
        List<String> ruleHits,
        String actionAdvice,
        MaterialPerformance7D metrics,
        MaterialBenchmark7D benchmark,
        Instant evaluatedAt
) {
    public MaterialQualityResult {
        reasonTags = reasonTags == null ? List.of() : List.copyOf(reasonTags);
        ruleHits = ruleHits == null ? List.of() : List.copyOf(ruleHits);
        score = score != null ? score : BigDecimal.ZERO;
        evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
        actionAdvice = actionAdvice != null ? actionAdvice : (qualityLevel != null ? qualityLevel.getDefaultAdvice() : "");
    }
}
