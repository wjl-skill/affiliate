package com.affiliate.platform.repository.quality;

import com.affiliate.platform.domain.quality.*;
import com.affiliate.platform.entity.CreativeQualityTagHistoryEntity;
import com.affiliate.platform.mapper.CreativeQualityTagHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 素材质量评估历史快照仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryMaterialQualityHistoryRepository implements MaterialQualityHistoryRepository {

    private final CreativeQualityTagHistoryMapper historyMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // key: id
    private final Map<String, MaterialQualityResult> store = new ConcurrentHashMap<>();

    public InMemoryMaterialQualityHistoryRepository() {
        this(null);
    }

    @Autowired
    public InMemoryMaterialQualityHistoryRepository(@Autowired(required = false) CreativeQualityTagHistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    @Override
    public MaterialQualityResult save(MaterialQualityResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (historyMapper != null) {
            try {
                String tagsJson = objectMapper.writeValueAsString(result.reasonTags());
                BigDecimal promoCost = result.metrics() != null ? result.metrics().promotionCost() : BigDecimal.ZERO;
                Long clicks = result.metrics() != null ? result.metrics().clicks() : 0L;
                Long installs = result.metrics() != null ? result.metrics().installNum() : 0L;
                BigDecimal roas = result.metrics() != null ? result.metrics().roas() : BigDecimal.ZERO;
                BigDecimal cpi = result.metrics() != null ? result.metrics().cpi() : BigDecimal.ZERO;
                BigDecimal ctr = result.metrics() != null ? result.metrics().ctr() : BigDecimal.ZERO;
                String benchmarkId = result.benchmark() != null ? result.benchmark().id() : null;

                CreativeQualityTagHistoryEntity entity = new CreativeQualityTagHistoryEntity(
                        result.id(),
                        result.tenantId(),
                        result.creativeId(),
                        result.statDate(),
                        result.qualityLevel() != null ? result.qualityLevel().name() : "UNRATED",
                        result.score(),
                        promoCost,
                        clicks,
                        installs,
                        roas,
                        cpi,
                        ctr,
                        tagsJson,
                        benchmarkId,
                        result.evaluatedAt() != null ? result.evaluatedAt() : Instant.now()
                );
                if (historyMapper.selectById(entity.getId()) != null) {
                    historyMapper.updateById(entity);
                } else {
                    historyMapper.insert(entity);
                }
            } catch (Exception ignored) {}
        }
        store.put(result.id(), result);
        return result;
    }

    @Override
    public Optional<MaterialQualityResult> findLatestByCreativeId(String tenantId, String creativeId) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        if (historyMapper != null) {
            try {
                QueryWrapper<CreativeQualityTagHistoryEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", t).eq("creative_id", creativeId).orderByDesc("stat_date").last("LIMIT 1");
                CreativeQualityTagHistoryEntity entity = historyMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception ignored) {}
        }
        return store.values().stream()
                .filter(r -> t.equals(r.tenantId()) && creativeId.equals(r.creativeId()))
                .max(Comparator.comparing(MaterialQualityResult::statDate));
    }

    @Override
    public List<MaterialQualityResult> findByCreativeId(String tenantId, String creativeId, int limit) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        int max = (limit <= 0) ? 30 : Math.min(limit, 365);
        if (historyMapper != null) {
            try {
                QueryWrapper<CreativeQualityTagHistoryEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", t).eq("creative_id", creativeId).orderByDesc("stat_date").last("LIMIT " + max);
                List<CreativeQualityTagHistoryEntity> list = historyMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception ignored) {}
        }
        return store.values().stream()
                .filter(r -> t.equals(r.tenantId()) && creativeId.equals(r.creativeId()))
                .sorted(Comparator.comparing(MaterialQualityResult::statDate).reversed())
                .limit(max)
                .toList();
    }

    @Override
    public List<MaterialQualityResult> findByDateAndLevel(String tenantId, LocalDate date, MaterialQualityLevel level) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        LocalDate d = (date != null) ? date : LocalDate.now();
        if (historyMapper != null) {
            try {
                QueryWrapper<CreativeQualityTagHistoryEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", t).eq("stat_date", d);
                if (level != null) {
                    qw.eq("quality_level", level.name());
                }
                qw.orderByDesc("score");
                List<CreativeQualityTagHistoryEntity> list = historyMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception ignored) {}
        }
        return store.values().stream()
                .filter(r -> t.equals(r.tenantId()) && d.equals(r.statDate()) && (level == null || level == r.qualityLevel()))
                .sorted(Comparator.comparing(MaterialQualityResult::score).reversed())
                .toList();
    }

    private MaterialQualityResult toDomain(CreativeQualityTagHistoryEntity entity) {
        List<String> tags = List.of();
        if (entity.getReasonTags() != null && !entity.getReasonTags().isBlank()) {
            try {
                tags = objectMapper.readValue(entity.getReasonTags(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }
        MaterialQualityLevel level;
        try {
            level = MaterialQualityLevel.valueOf(entity.getQualityLevel());
        } catch (Exception e) {
            level = MaterialQualityLevel.UNRATED;
        }

        BigDecimal cost = entity.getPromotionCost7d() != null ? entity.getPromotionCost7d() : BigDecimal.ZERO;
        BigDecimal roas = entity.getRoas7d() != null ? entity.getRoas7d() : BigDecimal.ZERO;

        MaterialPerformance7D metrics = new MaterialPerformance7D(
                entity.getCreativeId(),
                entity.getCreativeId(),
                "Material-" + entity.getCreativeId(),
                "",
                entity.getStatDate(),
                cost,
                0L,
                entity.getClicks7d() != null ? entity.getClicks7d() : 0L,
                entity.getInstalls7d() != null ? entity.getInstalls7d() : 0L,
                cost.multiply(roas)
        );

        return new MaterialQualityResult(
                entity.getId(),
                entity.getTenantId(),
                entity.getCreativeId(),
                "Material-" + entity.getCreativeId(),
                entity.getStatDate(),
                level,
                MaterialSampleStatus.SUFFICIENT,
                entity.getScore() != null ? entity.getScore() : BigDecimal.ZERO,
                tags,
                List.of(),
                level.getDefaultAdvice(),
                metrics,
                null,
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now()
        );
    }

    public void clear() {
        store.clear();
    }
}
