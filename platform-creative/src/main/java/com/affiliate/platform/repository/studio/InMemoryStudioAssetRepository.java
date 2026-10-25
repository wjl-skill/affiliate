package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.StudioAsset;
import com.affiliate.platform.domain.studio.StudioAssetStatus;
import com.affiliate.platform.domain.studio.StudioAssetType;
import com.affiliate.platform.entity.CreativeStudioAssetEntity;
import com.affiliate.platform.mapper.CreativeStudioAssetMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 长视频资产持久化仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryStudioAssetRepository implements StudioAssetRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStudioAssetRepository.class);

    private final CreativeStudioAssetMapper assetMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StudioAsset> storage = new ConcurrentHashMap<>();

    public InMemoryStudioAssetRepository() {
        this(null);
    }

    @Autowired
    public InMemoryStudioAssetRepository(@Autowired(required = false) CreativeStudioAssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    private String key(String tenantId, String id) {
        return (tenantId != null ? tenantId : "public") + ":" + id;
    }

    @Override
    public StudioAsset save(StudioAsset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("asset cannot be null");
        }
        if (assetMapper != null) {
            try {
                String promptDataJson = asset.promptData() != null ? objectMapper.writeValueAsString(asset.promptData()) : "{}";
                CreativeStudioAssetEntity entity = new CreativeStudioAssetEntity(
                        asset.id(),
                        asset.tenantId(),
                        asset.projectId(),
                        asset.assetType() != null ? asset.assetType().name() : "PRODUCT",
                        asset.name(),
                        asset.description(),
                        promptDataJson,
                        asset.imageUrl(),
                        asset.status() != null ? asset.status().name() : "DRAFT",
                        asset.createdAt() != null ? asset.createdAt() : Instant.now()
                );
                if (assetMapper.selectById(entity.getId()) != null) {
                    assetMapper.updateById(entity);
                } else {
                    assetMapper.insert(entity);
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioAssetRepository] Database operation notice: {}", e.getMessage());}
        }
        storage.put(key(asset.tenantId(), asset.id()), asset);
        return asset;
    }

    @Override
    public List<StudioAsset> saveAll(List<StudioAsset> assets) {
        if (assets != null) {
            for (StudioAsset a : assets) {
                save(a);
            }
        }
        return assets;
    }

    @Override
    public Optional<StudioAsset> findById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (assetMapper != null) {
            try {
                QueryWrapper<CreativeStudioAssetEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                CreativeStudioAssetEntity entity = assetMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioAssetRepository] Database operation notice: {}", e.getMessage());}
        }
        StudioAsset found = storage.get(key(tenantId, id));
        if (found != null) {
            return Optional.of(found);
        }
        return storage.values().stream()
                .filter(a -> a.id().equals(id))
                .findFirst();
    }

    @Override
    public List<StudioAsset> findByProjectId(String tenantId, String projectId) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (assetMapper != null) {
            try {
                QueryWrapper<CreativeStudioAssetEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant).eq("project_id", projectId).orderByAsc("created_at");
                List<CreativeStudioAssetEntity> list = assetMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioAssetRepository] Database operation notice: {}", e.getMessage());}
        }
        return storage.values().stream()
                .filter(a -> a.tenantId().equals(effectiveTenant) && a.projectId().equals(projectId))
                .sorted(Comparator.comparing(StudioAsset::createdAt))
                .toList();
    }

    private StudioAsset toDomain(CreativeStudioAssetEntity entity) {
        StudioAssetType type;
        try {
            type = StudioAssetType.valueOf(entity.getAssetType());
        } catch (Exception e) {
            type = StudioAssetType.PRODUCT;
        }

        StudioAssetStatus status;
        try {
            status = StudioAssetStatus.valueOf(entity.getStatus());
        } catch (Exception e) {
            status = StudioAssetStatus.DRAFT;
        }

        Map<String, Object> promptData = Map.of();
        if (entity.getPromptDataJson() != null && !entity.getPromptDataJson().isBlank()) {
            try {
                promptData = objectMapper.readValue(entity.getPromptDataJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {    log.debug("[InMemoryStudioAssetRepository] Database operation notice: {}", e.getMessage());}
        }

        return new StudioAsset(
                entity.getId(),
                entity.getTenantId(),
                entity.getProjectId(),
                type,
                entity.getName(),
                entity.getDescription() != null ? entity.getDescription() : "",
                promptData,
                entity.getImageUrl(),
                status,
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now()
        );
    }

    public void clear() {
        storage.clear();
    }
}
