package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.domain.studio.StudioProjectStatus;
import com.affiliate.platform.entity.CreativeStudioProjectEntity;
import com.affiliate.platform.mapper.CreativeStudioProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 长视频项目持久化仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryStudioProjectRepository implements StudioProjectRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStudioProjectRepository.class);

    private final CreativeStudioProjectMapper projectMapper;
    private final Map<String, StudioProject> storage = new ConcurrentHashMap<>();

    public InMemoryStudioProjectRepository() {
        this(null);
    }

    @Autowired
    public InMemoryStudioProjectRepository(@Autowired(required = false) CreativeStudioProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    private String key(String tenantId, String id) {
        return (tenantId != null ? tenantId : "public") + ":" + id;
    }

    @Override
    public StudioProject save(StudioProject project) {
        if (project == null) {
            throw new IllegalArgumentException("project cannot be null");
        }
        if (projectMapper != null) {
            try {
                CreativeStudioProjectEntity entity = new CreativeStudioProjectEntity(
                        project.id(),
                        project.tenantId(),
                        project.title(),
                        project.brief(),
                        project.aspectRatio(),
                        project.durationSeconds(),
                        project.targetPlatform(),
                        project.adType(),
                        project.creativeStyle(),
                        project.modelCode(),
                        project.status() != null ? project.status().name().toLowerCase() : "draft",
                        project.finalVideoUrl(),
                        project.capcutDraftUrl(),
                        project.createdBy(),
                        project.createdAt() != null ? project.createdAt() : Instant.now(),
                        project.updatedAt() != null ? project.updatedAt() : Instant.now()
                );
                if (projectMapper.selectById(entity.getId()) != null) {
                    projectMapper.updateById(entity);
                } else {
                    projectMapper.insert(entity);
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioProjectRepository] Database operation notice: {}", e.getMessage());}
        }
        storage.put(key(project.tenantId(), project.id()), project);
        return project;
    }

    @Override
    public Optional<StudioProject> findById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (projectMapper != null) {
            try {
                QueryWrapper<CreativeStudioProjectEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                CreativeStudioProjectEntity entity = projectMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioProjectRepository] Database operation notice: {}", e.getMessage());}
        }
        StudioProject found = storage.get(key(tenantId, id));
        if (found != null) {
            return Optional.of(found);
        }
        return storage.values().stream()
                .filter(p -> p.id().equals(id))
                .findFirst();
    }

    @Override
    public List<StudioProject> listByTenant(String tenantId, int limit) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        int max = limit > 0 ? limit : 20;
        if (projectMapper != null) {
            try {
                QueryWrapper<CreativeStudioProjectEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant).orderByDesc("created_at").last("LIMIT " + max);
                List<CreativeStudioProjectEntity> list = projectMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioProjectRepository] Database operation notice: {}", e.getMessage());}
        }
        return storage.values().stream()
                .filter(p -> p.tenantId().equals(effectiveTenant))
                .sorted(Comparator.comparing(StudioProject::createdAt).reversed())
                .limit(max)
                .toList();
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (projectMapper != null) {
            try {
                QueryWrapper<CreativeStudioProjectEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                projectMapper.delete(qw);
            } catch (Exception e) {    log.debug("[InMemoryStudioProjectRepository] Database operation notice: {}", e.getMessage());}
        }
        return storage.remove(key(tenantId, id)) != null;
    }

    private StudioProject toDomain(CreativeStudioProjectEntity entity) {
        StudioProjectStatus status;
        try {
            status = StudioProjectStatus.valueOf(entity.getStatus().toUpperCase());
        } catch (Exception e) {
            status = StudioProjectStatus.DRAFT;
        }

        return new StudioProject(
                entity.getId(),
                entity.getTenantId(),
                entity.getTitle(),
                entity.getBrief(),
                entity.getAspectRatio(),
                entity.getDurationSeconds() != null ? entity.getDurationSeconds() : 30,
                entity.getTargetPlatform(),
                entity.getAdType(),
                entity.getCreativeStyle(),
                entity.getModelCode(),
                status,
                entity.getFinalVideoUrl(),
                entity.getCapcutDraftUrl(),
                entity.getCreatedBy(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now(),
                entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now()
        );
    }

    public void clear() {
        storage.clear();
    }
}
