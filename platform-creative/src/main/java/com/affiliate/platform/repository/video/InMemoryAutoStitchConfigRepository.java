package com.affiliate.platform.repository.video;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.AutoStitchConfig;
import com.affiliate.platform.domain.video.AutoStitchStatus;
import com.affiliate.platform.entity.CreativeAutoStitchConfigEntity;
import com.affiliate.platform.mapper.CreativeAutoStitchConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量混剪配置仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryAutoStitchConfigRepository implements AutoStitchConfigRepository {

    private final CreativeAutoStitchConfigMapper configMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AutoStitchConfig> storage = new ConcurrentHashMap<>();

    public InMemoryAutoStitchConfigRepository() {
        this(null);
    }

    @Autowired
    public InMemoryAutoStitchConfigRepository(@Autowired(required = false) CreativeAutoStitchConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    private String key(String tenantId, String id) {
        return (tenantId != null ? tenantId : "public") + ":" + id;
    }

    @Override
    public AutoStitchConfig save(AutoStitchConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        if (configMapper != null) {
            try {
                Map<String, Object> sourcesMap = new LinkedHashMap<>();
                sourcesMap.put("introVideoUrls", config.introVideoUrls());
                sourcesMap.put("bodyVideoUrls", config.bodyVideoUrls());
                String folderSourcesJson = objectMapper.writeValueAsString(sourcesMap);
                String ctaJson = config.ctaConfig() != null ? objectMapper.writeValueAsString(config.ctaConfig()) : "{}";

                CreativeAutoStitchConfigEntity entity = new CreativeAutoStitchConfigEntity(
                        config.id(),
                        config.tenantId(),
                        config.configName(),
                        folderSourcesJson,
                        config.targetAspectRatio() != null ? config.targetAspectRatio() : "9:16",
                        config.alignMode() != null ? config.alignMode().name() : "LETTERBOX",
                        ctaJson,
                        config.scheduledTime(),
                        config.status() != null ? config.status().name() : "PENDING",
                        config.createdAt() != null ? config.createdAt() : Instant.now()
                );
                if (configMapper.selectById(entity.getId()) != null) {
                    configMapper.updateById(entity);
                } else {
                    configMapper.insert(entity);
                }
            } catch (Exception ignored) {}
        }
        storage.put(key(config.tenantId(), config.id()), config);
        return config;
    }

    @Override
    public Optional<AutoStitchConfig> findById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (configMapper != null) {
            try {
                QueryWrapper<CreativeAutoStitchConfigEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                CreativeAutoStitchConfigEntity entity = configMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception ignored) {}
        }
        AutoStitchConfig found = storage.get(key(tenantId, id));
        if (found != null) {
            return Optional.of(found);
        }
        return storage.values().stream()
                .filter(c -> c.id().equals(id))
                .findFirst();
    }

    @Override
    public List<AutoStitchConfig> listByTenant(String tenantId) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (configMapper != null) {
            try {
                QueryWrapper<CreativeAutoStitchConfigEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant).orderByDesc("created_at");
                List<CreativeAutoStitchConfigEntity> list = configMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception ignored) {}
        }
        return storage.values().stream()
                .filter(c -> c.tenantId().equals(effectiveTenant))
                .sorted(Comparator.comparing(AutoStitchConfig::createdAt).reversed())
                .toList();
    }

    @Override
    public List<AutoStitchConfig> findPendingSchedules(Instant beforeOrEqualTime) {
        if (configMapper != null) {
            try {
                QueryWrapper<CreativeAutoStitchConfigEntity> qw = new QueryWrapper<>();
                qw.eq("status", "PENDING");
                if (beforeOrEqualTime != null) {
                    qw.le("scheduled_time", beforeOrEqualTime);
                }
                qw.orderByAsc("scheduled_time");
                List<CreativeAutoStitchConfigEntity> list = configMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception ignored) {}
        }
        return storage.values().stream()
                .filter(c -> c.status() == AutoStitchStatus.PENDING &&
                        (beforeOrEqualTime == null || c.scheduledTime() == null || !c.scheduledTime().isAfter(beforeOrEqualTime)))
                .sorted(Comparator.comparing(AutoStitchConfig::scheduledTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (configMapper != null) {
            try {
                QueryWrapper<CreativeAutoStitchConfigEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                configMapper.delete(qw);
            } catch (Exception ignored) {}
        }
        return storage.remove(key(tenantId, id)) != null;
    }

    private AutoStitchConfig toDomain(CreativeAutoStitchConfigEntity entity) {
        AlignMode align;
        try {
            align = AlignMode.valueOf(entity.getAlignMode());
        } catch (Exception e) {
            align = AlignMode.LETTERBOX;
        }

        AutoStitchStatus status;
        try {
            status = AutoStitchStatus.valueOf(entity.getStatus());
        } catch (Exception e) {
            status = AutoStitchStatus.PENDING;
        }

        List<String> intros = List.of();
        List<String> bodies = List.of();
        if (entity.getFolderSources() != null && !entity.getFolderSources().isBlank()) {
            try {
                Map<String, Object> map = objectMapper.readValue(entity.getFolderSources(), new TypeReference<Map<String, Object>>() {});
                if (map.get("introVideoUrls") instanceof List<?> list) {
                    intros = list.stream().map(Object::toString).toList();
                }
                if (map.get("bodyVideoUrls") instanceof List<?> list) {
                    bodies = list.stream().map(Object::toString).toList();
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> cta = Map.of();
        if (entity.getCtaConfig() != null && !entity.getCtaConfig().isBlank()) {
            try {
                cta = objectMapper.readValue(entity.getCtaConfig(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        return new AutoStitchConfig(
                entity.getId(),
                entity.getTenantId(),
                entity.getConfigName(),
                intros,
                bodies,
                entity.getTargetAspectRatio() != null ? entity.getTargetAspectRatio() : "9:16",
                align,
                cta,
                entity.getScheduledTime() != null ? entity.getScheduledTime() : Instant.now(),
                status,
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now()
        );
    }

    public void clear() {
        storage.clear();
    }
}
