package com.affiliate.platform.repository.quality;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.quality.MaterialQualityResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 素材质量评估历史快照仓储端口 (Material Quality History Repository Port)
 */
public interface MaterialQualityHistoryRepository {

    /**
     * 持久化评级快照结果
     */
    MaterialQualityResult save(MaterialQualityResult result);

    /**
     * 查询指定素材的最新一期评级
     */
    Optional<MaterialQualityResult> findLatestByCreativeId(String tenantId, String creativeId);

    /**
     * 查询指定素材的历史评级记录列表（按时间倒序）
     */
    List<MaterialQualityResult> findByCreativeId(String tenantId, String creativeId, int limit);

    /**
     * 按统计日期和质量等级查询素材列表
     */
    List<MaterialQualityResult> findByDateAndLevel(String tenantId, LocalDate date, MaterialQualityLevel level);
}
