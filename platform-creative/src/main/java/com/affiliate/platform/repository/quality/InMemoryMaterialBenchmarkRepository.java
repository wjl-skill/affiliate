package com.affiliate.platform.repository.quality;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import com.affiliate.platform.entity.CreativeBenchmark7dEntity;
import com.affiliate.platform.mapper.CreativeBenchmark7dMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 7日大盘基准线持久化仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryMaterialBenchmarkRepository implements MaterialBenchmarkRepository {

    private final CreativeBenchmark7dMapper benchmarkMapper;
    private final Map<String, MaterialBenchmark7D> store = new ConcurrentHashMap<>();

    public InMemoryMaterialBenchmarkRepository() {
        this(null);
    }

    @Autowired
    public InMemoryMaterialBenchmarkRepository(@Autowired(required = false) CreativeBenchmark7dMapper benchmarkMapper) {
        this.benchmarkMapper = benchmarkMapper;
    }

    private String key(String tenantId, LocalDate date) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        LocalDate d = (date != null) ? date : LocalDate.now();
        return t + ":" + d;
    }

    @Override
    public MaterialBenchmark7D save(MaterialBenchmark7D benchmark) {
        if (benchmark == null) {
            throw new IllegalArgumentException("benchmark cannot be null");
        }
        if (benchmarkMapper != null) {
            try {
                CreativeBenchmark7dEntity entity = new CreativeBenchmark7dEntity(
                        benchmark.id(),
                        benchmark.tenantId(),
                        benchmark.statDate(),
                        benchmark.windowStartDate(),
                        benchmark.windowEndDate(),
                        benchmark.materialSampleCount(),
                        benchmark.sampleCostThreshold(),
                        benchmark.sampleClickThreshold(),
                        benchmark.sampleInstallThreshold(),
                        benchmark.roasP75(),
                        benchmark.roasP25(),
                        benchmark.costP50(),
                        benchmark.cpiAvg(),
                        benchmark.ctrAvg(),
                        benchmark.cvrInstallAvg(),
                        benchmark.createdAt() != null ? benchmark.createdAt() : Instant.now()
                );
                if (benchmarkMapper.selectById(entity.getId()) != null) {
                    benchmarkMapper.updateById(entity);
                } else {
                    benchmarkMapper.insert(entity);
                }
            } catch (Exception ignored) {}
        }
        store.put(key(benchmark.tenantId(), benchmark.statDate()), benchmark);
        return benchmark;
    }

    @Override
    public Optional<MaterialBenchmark7D> findLatest(String tenantId) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        if (benchmarkMapper != null) {
            try {
                QueryWrapper<CreativeBenchmark7dEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", t).orderByDesc("stat_date").last("LIMIT 1");
                CreativeBenchmark7dEntity entity = benchmarkMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception ignored) {}
        }
        return store.values().stream()
                .filter(b -> t.equals(b.tenantId()))
                .max(Comparator.comparing(MaterialBenchmark7D::statDate));
    }

    @Override
    public Optional<MaterialBenchmark7D> findByDate(String tenantId, LocalDate date) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        LocalDate d = (date != null) ? date : LocalDate.now();
        if (benchmarkMapper != null) {
            try {
                QueryWrapper<CreativeBenchmark7dEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", t).eq("stat_date", d).last("LIMIT 1");
                CreativeBenchmark7dEntity entity = benchmarkMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception ignored) {}
        }
        return Optional.ofNullable(store.get(key(tenantId, date)));
    }

    private MaterialBenchmark7D toDomain(CreativeBenchmark7dEntity entity) {
        LocalDate s = entity.getStatDate() != null ? entity.getStatDate() : LocalDate.now();
        LocalDate start = entity.getWindowStartDate() != null ? entity.getWindowStartDate() : s.minusDays(6);
        LocalDate end = entity.getWindowEndDate() != null ? entity.getWindowEndDate() : s;
        return new MaterialBenchmark7D(
                entity.getId(),
                entity.getTenantId(),
                s,
                start,
                end,
                entity.getMaterialSampleCount() != null ? entity.getMaterialSampleCount() : 0,
                entity.getSampleCostThreshold() != null ? entity.getSampleCostThreshold() : MaterialBenchmark7D.DEFAULT_COST_THRESHOLD,
                entity.getSampleClickThreshold() != null ? entity.getSampleClickThreshold() : MaterialBenchmark7D.DEFAULT_CLICK_THRESHOLD,
                entity.getSampleInstallThreshold() != null ? entity.getSampleInstallThreshold() : MaterialBenchmark7D.DEFAULT_INSTALL_THRESHOLD,
                entity.getRoasP75() != null ? entity.getRoasP75() : BigDecimal.valueOf(1.80),
                entity.getRoasP25() != null ? entity.getRoasP25() : BigDecimal.valueOf(0.90),
                entity.getCostP50() != null ? entity.getCostP50() : BigDecimal.valueOf(800.00),
                entity.getCpiAvg() != null ? entity.getCpiAvg() : BigDecimal.valueOf(12.50),
                entity.getCtrAvg() != null ? entity.getCtrAvg() : BigDecimal.valueOf(0.025),
                entity.getCvrInstallAvg() != null ? entity.getCvrInstallAvg() : BigDecimal.valueOf(0.045),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now()
        );
    }

    public void clear() {
        store.clear();
    }
}
