package com.affiliate.platform.repository.ai;

import com.affiliate.platform.domain.ai.AiGenerateTaskRecord;
import com.affiliate.platform.domain.ai.AiGenerateType;
import com.affiliate.platform.domain.ai.AiTaskStatus;
import com.affiliate.platform.entity.CreativeAiTaskRecordEntity;
import com.affiliate.platform.mapper.CreativeAiTaskRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 生成任务持久化仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryAiGenerateTaskRecordRepository implements AiGenerateTaskRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAiGenerateTaskRecordRepository.class);

    private final CreativeAiTaskRecordMapper taskMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // key: id
    private final Map<String, AiGenerateTaskRecord> store = new ConcurrentHashMap<>();

    public InMemoryAiGenerateTaskRecordRepository() {
        this(null);
    }

    @Autowired
    public InMemoryAiGenerateTaskRecordRepository(@Autowired(required = false) CreativeAiTaskRecordMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public AiGenerateTaskRecord save(AiGenerateTaskRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record cannot be null");
        }
        if (taskMapper != null) {
            try {
                Map<String, Object> assetsMap = new LinkedHashMap<>();
                if (record.startImageUrl() != null) assetsMap.put("startImageUrl", record.startImageUrl());
                if (record.endImageUrl() != null) assetsMap.put("endImageUrl", record.endImageUrl());
                if (record.referenceImageUrls() != null) assetsMap.put("referenceImageUrls", record.referenceImageUrls());
                if (record.sourceVideoUrl() != null) assetsMap.put("sourceVideoUrl", record.sourceVideoUrl());
                String assetsJson = objectMapper.writeValueAsString(assetsMap);

                CreativeAiTaskRecordEntity entity = new CreativeAiTaskRecordEntity(
                        record.id(),
                        record.tenantId(),
                        record.taskNo(),
                        record.generateType() != null ? record.generateType().name() : "TEXT_TO_VIDEO",
                        record.modelCode(),
                        record.providerCode(),
                        record.remoteTaskId(),
                        record.status() != null ? record.status().name() : "PENDING",
                        record.prompt(),
                        assetsJson,
                        record.rawOutputUrl(),
                        record.ossUrl(),
                        record.durationSeconds(),
                        record.usageUnit(),
                        record.usageQuantity(),
                        record.estimatedCost(),
                        record.costStatus(),
                        record.errorCode(),
                        record.errorMessage(),
                        record.createdBy(),
                        record.createdAt() != null ? record.createdAt() : Instant.now(),
                        record.finishedAt()
                );
                if (taskMapper.selectById(entity.getId()) != null) {
                    taskMapper.updateById(entity);
                } else {
                    taskMapper.insert(entity);
                }
            } catch (Exception e) {    log.debug("[InMemoryAiGenerateTaskRecordRepository] Database operation notice: {}", e.getMessage());}
        }
        store.put(record.id(), record);
        return record;
    }

    @Override
    public Optional<AiGenerateTaskRecord> findById(String id) {
        if (taskMapper != null) {
            try {
                CreativeAiTaskRecordEntity entity = taskMapper.selectById(id);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception e) {    log.debug("[InMemoryAiGenerateTaskRecordRepository] Database operation notice: {}", e.getMessage());}
        }
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<AiGenerateTaskRecord> findByTaskNo(String tenantId, String taskNo) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        if (taskMapper != null) {
            try {
                QueryWrapper<CreativeAiTaskRecordEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", t).eq("task_no", taskNo).last("LIMIT 1");
                CreativeAiTaskRecordEntity entity = taskMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception e) {    log.debug("[InMemoryAiGenerateTaskRecordRepository] Database operation notice: {}", e.getMessage());}
        }
        return store.values().stream()
                .filter(r -> t.equals(r.tenantId()) && taskNo.equals(r.taskNo()))
                .findFirst();
    }

    @Override
    public List<AiGenerateTaskRecord> findByStatus(AiTaskStatus status, int limit) {
        int max = limit > 0 ? limit : 50;
        if (taskMapper != null) {
            try {
                QueryWrapper<CreativeAiTaskRecordEntity> qw = new QueryWrapper<>();
                qw.eq("status", status.name()).orderByAsc("created_at").last("LIMIT " + max);
                List<CreativeAiTaskRecordEntity> list = taskMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryAiGenerateTaskRecordRepository] Database operation notice: {}", e.getMessage());}
        }
        return store.values().stream()
                .filter(r -> r.status() == status)
                .sorted(Comparator.comparing(AiGenerateTaskRecord::createdAt))
                .limit(max)
                .toList();
    }

    @Override
    public List<AiGenerateTaskRecord> findRecentByTenant(String tenantId, int limit) {
        String t = (tenantId == null || tenantId.isBlank()) ? "public" : tenantId;
        int max = limit > 0 ? limit : 20;
        if (taskMapper != null) {
            try {
                QueryWrapper<CreativeAiTaskRecordEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", t).orderByDesc("created_at").last("LIMIT " + max);
                List<CreativeAiTaskRecordEntity> list = taskMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryAiGenerateTaskRecordRepository] Database operation notice: {}", e.getMessage());}
        }
        return store.values().stream()
                .filter(r -> t.equals(r.tenantId()))
                .sorted(Comparator.comparing(AiGenerateTaskRecord::createdAt).reversed())
                .limit(max)
                .toList();
    }

    private AiGenerateTaskRecord toDomain(CreativeAiTaskRecordEntity entity) {
        AiGenerateType genType;
        try {
            genType = AiGenerateType.valueOf(entity.getGenerateType());
        } catch (Exception e) {
            genType = AiGenerateType.TEXT_TO_VIDEO;
        }

        AiTaskStatus taskStatus;
        try {
            taskStatus = AiTaskStatus.valueOf(entity.getStatus());
        } catch (Exception e) {
            taskStatus = AiTaskStatus.PENDING;
        }

        String startImg = null;
        String endImg = null;
        List<String> refs = List.of();
        String srcVideo = null;
        if (entity.getInputAssetsJson() != null && !entity.getInputAssetsJson().isBlank()) {
            try {
                Map<String, Object> map = objectMapper.readValue(entity.getInputAssetsJson(), new TypeReference<Map<String, Object>>() {});
                if (map.get("startImageUrl") != null) startImg = map.get("startImageUrl").toString();
                if (map.get("endImageUrl") != null) endImg = map.get("endImageUrl").toString();
                if (map.get("sourceVideoUrl") != null) srcVideo = map.get("sourceVideoUrl").toString();
                if (map.get("referenceImageUrls") instanceof List<?> list) {
                    refs = list.stream().map(Object::toString).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryAiGenerateTaskRecordRepository] Database operation notice: {}", e.getMessage());}
        }

        return new AiGenerateTaskRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getTaskNo(),
                genType,
                entity.getModelCode(),
                entity.getProviderCode(),
                entity.getRemoteTaskId(),
                taskStatus,
                entity.getPrompt(),
                startImg,
                endImg,
                refs,
                srcVideo,
                entity.getRawOutputUrl(),
                entity.getOssUrl(),
                entity.getDurationSeconds() != null ? entity.getDurationSeconds() : 0,
                entity.getUsageUnit() != null ? entity.getUsageUnit() : "SECOND",
                entity.getUsageQuantity() != null ? entity.getUsageQuantity() : BigDecimal.ZERO,
                entity.getEstimatedCost() != null ? entity.getEstimatedCost() : BigDecimal.ZERO,
                entity.getCostStatus() != null ? entity.getCostStatus() : "PENDING",
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreatedBy(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now(),
                entity.getFinishedAt()
        );
    }

    public void clear() {
        store.clear();
    }
}
