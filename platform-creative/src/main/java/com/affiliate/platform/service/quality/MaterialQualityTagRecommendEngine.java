package com.affiliate.platform.service.quality;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import com.affiliate.platform.domain.quality.MaterialPerformance7D;
import com.affiliate.platform.domain.quality.MaterialQualityResult;

/**
 * 素材质量标签评估与推荐引擎接口 (Material Quality Tag Recommend Engine)
 * <p>
 * 负责依据大盘动态基准线对素材近 7 日投放事实执行严格判级算法。
 */
public interface MaterialQualityTagRecommendEngine {

    /**
     * 对单个素材执行质量评定计算
     *
     * @param performance 素材 7 日投放事实指标，不可为 null
     * @param benchmark   对应基准日期的大盘质量基准线快照，不可为 null
     * @return 评级结论与明细
     */
    MaterialQualityResult evaluate(MaterialPerformance7D performance, MaterialBenchmark7D benchmark);
}
