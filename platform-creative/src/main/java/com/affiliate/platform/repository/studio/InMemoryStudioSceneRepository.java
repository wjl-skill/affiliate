package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.StudioScene;
import com.affiliate.platform.domain.studio.StudioSceneStatus;
import com.affiliate.platform.domain.studio.TransitionType;
import com.affiliate.platform.entity.CreativeStudioSceneEntity;
import com.affiliate.platform.mapper.CreativeStudioSceneMapper;
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
 * AI 长视频工作台单分镜镜头持久化仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryStudioSceneRepository implements StudioSceneRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStudioSceneRepository.class);

    private final CreativeStudioSceneMapper sceneMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StudioScene> storage = new ConcurrentHashMap<>();

    public InMemoryStudioSceneRepository() {
        this(null);
    }

    @Autowired
    public InMemoryStudioSceneRepository(@Autowired(required = false) CreativeStudioSceneMapper sceneMapper) {
        this.sceneMapper = sceneMapper;
    }

    private String key(String tenantId, String id) {
        return (tenantId != null ? tenantId : "public") + ":" + id;
    }

    @Override
    public StudioScene save(StudioScene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("scene cannot be null");
        }
        if (sceneMapper != null) {
            try {
                String keywordsJson = scene.subtitleKeywords() != null
                        ? objectMapper.writeValueAsString(scene.subtitleKeywords())
                        : "[]";
                CreativeStudioSceneEntity entity = new CreativeStudioSceneEntity(
                        scene.id(),
                        scene.tenantId(),
                        scene.projectId(),
                        scene.sceneIndex(),
                        scene.durationSeconds(),
                        scene.shotPrompt(),
                        scene.voiceoverText(),
                        scene.voiceoverTone() != null ? scene.voiceoverTone() : "温暖女声",
                        scene.voiceoverSpeed() != null ? scene.voiceoverSpeed() : "normal",
                        scene.subtitleText(),
                        keywordsJson,
                        scene.bgmCue(),
                        scene.transitionType() != null ? scene.transitionType().name() : "chain_extend",
                        scene.transitionDesc(),
                        scene.startKeyframeUrl(),
                        scene.endKeyframeUrl(),
                        scene.videoClipUrl(),
                        scene.taskId(),
                        scene.status() != null ? scene.status().name() : "DRAFT",
                        scene.createdAt() != null ? scene.createdAt() : Instant.now()
                );
                if (sceneMapper.selectById(entity.getId()) != null) {
                    sceneMapper.updateById(entity);
                } else {
                    sceneMapper.insert(entity);
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioSceneRepository] Database operation notice: {}", e.getMessage());}
        }
        storage.put(key(scene.tenantId(), scene.id()), scene);
        return scene;
    }

    @Override
    public List<StudioScene> saveAll(List<StudioScene> scenes) {
        if (scenes != null) {
            for (StudioScene s : scenes) {
                save(s);
            }
        }
        return scenes;
    }

    @Override
    public Optional<StudioScene> findById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (sceneMapper != null) {
            try {
                QueryWrapper<CreativeStudioSceneEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                CreativeStudioSceneEntity entity = sceneMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioSceneRepository] Database operation notice: {}", e.getMessage());}
        }
        StudioScene found = storage.get(key(tenantId, id));
        if (found != null) {
            return Optional.of(found);
        }
        return storage.values().stream()
                .filter(s -> s.id().equals(id))
                .findFirst();
    }

    @Override
    public List<StudioScene> findByProjectId(String tenantId, String projectId) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (sceneMapper != null) {
            try {
                QueryWrapper<CreativeStudioSceneEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant).eq("project_id", projectId).orderByAsc("scene_index");
                List<CreativeStudioSceneEntity> list = sceneMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryStudioSceneRepository] Database operation notice: {}", e.getMessage());}
        }
        return storage.values().stream()
                .filter(s -> s.tenantId().equals(effectiveTenant) && s.projectId().equals(projectId))
                .sorted(Comparator.comparingInt(StudioScene::sceneIndex))
                .toList();
    }

    @Override
    public void deleteByProjectId(String tenantId, String projectId) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (sceneMapper != null) {
            try {
                QueryWrapper<CreativeStudioSceneEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant).eq("project_id", projectId);
                sceneMapper.delete(qw);
            } catch (Exception e) {    log.debug("[InMemoryStudioSceneRepository] Database operation notice: {}", e.getMessage());}
        }
        storage.entrySet().removeIf(e -> {
            StudioScene s = e.getValue();
            return s.tenantId().equals(effectiveTenant) && s.projectId().equals(projectId);
        });
    }

    private StudioScene toDomain(CreativeStudioSceneEntity entity) {
        TransitionType trans;
        try {
            trans = TransitionType.valueOf(entity.getTransitionType());
        } catch (Exception e) {
            trans = TransitionType.CHAIN_EXTEND;
        }

        StudioSceneStatus status;
        try {
            status = StudioSceneStatus.valueOf(entity.getStatus());
        } catch (Exception e) {
            status = StudioSceneStatus.DRAFT;
        }

        List<String> keywords = List.of();
        if (entity.getSubtitleKeywords() != null && !entity.getSubtitleKeywords().isBlank()) {
            try {
                keywords = objectMapper.readValue(entity.getSubtitleKeywords(), new TypeReference<List<String>>() {});
            } catch (Exception e) {    log.debug("[InMemoryStudioSceneRepository] Database operation notice: {}", e.getMessage());}
        }

        return new StudioScene(
                entity.getId(),
                entity.getTenantId(),
                entity.getProjectId(),
                entity.getSceneIndex() != null ? entity.getSceneIndex() : 0,
                entity.getDurationSeconds() != null ? entity.getDurationSeconds() : 5,
                entity.getShotPrompt(),
                entity.getVoiceoverText(),
                entity.getVoiceoverTone() != null ? entity.getVoiceoverTone() : "温暖女声",
                entity.getVoiceoverSpeed() != null ? entity.getVoiceoverSpeed() : "normal",
                entity.getSubtitleText(),
                keywords,
                entity.getBgmCue(),
                trans,
                entity.getTransitionDesc(),
                entity.getStartKeyframeUrl(),
                entity.getEndKeyframeUrl(),
                entity.getVideoClipUrl(),
                entity.getTaskId(),
                status,
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now()
        );
    }

    public void clear() {
        storage.clear();
    }
}
