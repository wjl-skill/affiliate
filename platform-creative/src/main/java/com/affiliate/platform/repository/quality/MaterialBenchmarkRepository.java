package com.affiliate.platform.repository.quality;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 7日大盘质量基准线仓储端口 (Material Benchmark Repository Port)
 */
public interface MaterialBenchmarkRepository {

    /**
     * 保存或更新基准线记录
     */
    MaterialBenchmark7D save(MaterialBenchmark7D benchmark);

    /**
     * 获取指定租户最新一期大盘基准快照
     */
    Optional<MaterialBenchmark7D> findLatest(String tenantId);

    /**
     * 根据统计日期查询基准线
     */
    Optional<MaterialBenchmark7D> findByDate(String tenantId, LocalDate date);
}
