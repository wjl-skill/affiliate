package com.affiliate.platform.repository.video;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.VideoProcessStatus;
import com.affiliate.platform.domain.video.VideoProcessTaskRecord;
import com.affiliate.platform.domain.video.VideoProcessType;
import com.affiliate.platform.entity.CreativeVideoProcessTaskEntity;
import com.affiliate.platform.mapper.CreativeVideoProcessTaskMapper;
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
 * 视频处理任务仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryVideoProcessTaskRepository implements VideoProcessTaskRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVideoProcessTaskRepository.class);

    private final CreativeVideoProcessTaskMapper videoTaskMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, VideoProcessTaskRecord> storage = new ConcurrentHashMap<>();

    public InMemoryVideoProcessTaskRepository() {
        this(null);
    }

    @Autowired
    public InMemoryVideoProcessTaskRepository(@Autowired(required = false) CreativeVideoProcessTaskMapper videoTaskMapper) {
        this.videoTaskMapper = videoTaskMapper;
    }

    private String key(String tenantId, String id) {
        return (tenantId != null ? tenantId : "public") + ":" + id;
    }

    @Override
    public VideoProcessTaskRecord save(VideoProcessTaskRecord task) {
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        if (videoTaskMapper != null) {
            try {
                String paramsJson = task.parameters() != null ? objectMapper.writeValueAsString(task.parameters()) : "{}";
                CreativeVideoProcessTaskEntity entity = new CreativeVideoProcessTaskEntity(
                        task.id(),
                        task.tenantId(),
                        task.taskNo() != null ? task.taskNo() : task.id(),
                        task.processType() != null ? task.processType().name() : "AUTO_STITCH",
                        task.sourceVideoUrl(),
                        task.introVideoUrl(),
                        task.targetWidth() != null ? task.targetWidth() : 1080,
                        task.targetHeight() != null ? task.targetHeight() : 1920,
                        task.alignMode() != null ? task.alignMode().name() : "LETTERBOX",
                        task.status() != null ? task.status().name() : "PENDING",
                        task.outputVideoUrl(),
                        task.outputCoverUrl(),
                        task.durationSeconds(),
                        task.fileSizeBytes(),
                        task.errorMessage(),
                        paramsJson,
                        task.createdAt() != null ? task.createdAt() : Instant.now(),
                        task.finishedAt()
                );
                if (videoTaskMapper.selectById(entity.getId()) != null) {
                    videoTaskMapper.updateById(entity);
                } else {
                    videoTaskMapper.insert(entity);
                }
            } catch (Exception e) {    log.debug("[InMemoryVideoProcessTaskRepository] Database operation notice: {}", e.getMessage());}
        }
        storage.put(key(task.tenantId(), task.id()), task);
        return task;
    }

    @Override
    public Optional<VideoProcessTaskRecord> findById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (videoTaskMapper != null) {
            try {
                QueryWrapper<CreativeVideoProcessTaskEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                CreativeVideoProcessTaskEntity entity = videoTaskMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception e) {    log.debug("[InMemoryVideoProcessTaskRepository] Database operation notice: {}", e.getMessage());}
        }
        VideoProcessTaskRecord found = storage.get(key(tenantId, id));
        if (found != null) {
            return Optional.of(found);
        }
        return storage.values().stream()
                .filter(t -> t.id().equals(id))
                .findFirst();
    }

    @Override
    public Optional<VideoProcessTaskRecord> findByTaskNo(String tenantId, String taskNo) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (videoTaskMapper != null) {
            try {
                QueryWrapper<CreativeVideoProcessTaskEntity> qw = new QueryWrapper<>();
                qw.eq("task_no", taskNo);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                CreativeVideoProcessTaskEntity entity = videoTaskMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception e) {    log.debug("[InMemoryVideoProcessTaskRepository] Database operation notice: {}", e.getMessage());}
        }
        return storage.values().stream()
                .filter(t -> (tenantId == null || tenantId.isBlank() || t.tenantId().equals(effectiveTenant))
                        && taskNo.equals(t.taskNo()))
                .findFirst();
    }

    @Override
    public List<VideoProcessTaskRecord> findByStatus(String tenantId, VideoProcessStatus status) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (videoTaskMapper != null) {
            try {
                QueryWrapper<CreativeVideoProcessTaskEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant);
                if (status != null) {
                    qw.eq("status", status.name());
                }
                qw.orderByAsc("created_at");
                List<CreativeVideoProcessTaskEntity> list = videoTaskMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryVideoProcessTaskRepository] Database operation notice: {}", e.getMessage());}
        }
        return storage.values().stream()
                .filter(t -> t.tenantId().equals(effectiveTenant) && (status == null || t.status() == status))
                .sorted(Comparator.comparing(VideoProcessTaskRecord::createdAt))
                .toList();
    }

    @Override
    public List<VideoProcessTaskRecord> listRecent(String tenantId, int limit) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        if (videoTaskMapper != null) {
            try {
                QueryWrapper<CreativeVideoProcessTaskEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant).orderByDesc("created_at").last("LIMIT " + max);
                List<CreativeVideoProcessTaskEntity> list = videoTaskMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception e) {    log.debug("[InMemoryVideoProcessTaskRepository] Database operation notice: {}", e.getMessage());}
        }
        return storage.values().stream()
                .filter(t -> t.tenantId().equals(effectiveTenant))
                .sorted(Comparator.comparing(VideoProcessTaskRecord::createdAt).reversed())
                .limit(max)
                .toList();
    }

    private VideoProcessTaskRecord toDomain(CreativeVideoProcessTaskEntity entity) {
        VideoProcessType procType;
        try {
            procType = VideoProcessType.valueOf(entity.getProcessType());
        } catch (Exception e) {
            procType = VideoProcessType.BATCH_ASSEMBLE;
        }

        VideoProcessStatus status;
        try {
            status = VideoProcessStatus.valueOf(entity.getStatus());
        } catch (Exception e) {
            status = VideoProcessStatus.PENDING;
        }

        AlignMode align;
        try {
            align = AlignMode.valueOf(entity.getAlignMode());
        } catch (Exception e) {
            align = AlignMode.LETTERBOX;
        }

        Map<String, Object> params = Map.of();
        if (entity.getOperatorParamsJson() != null && !entity.getOperatorParamsJson().isBlank()) {
            try {
                params = objectMapper.readValue(entity.getOperatorParamsJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {    log.debug("[InMemoryVideoProcessTaskRepository] Database operation notice: {}", e.getMessage());}
        }

        return new VideoProcessTaskRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getTaskNo() != null ? entity.getTaskNo() : entity.getId(),
                procType,
                entity.getSourceVideoUrl(),
                entity.getIntroVideoUrl(),
                entity.getTargetWidth() != null ? entity.getTargetWidth() : 1080,
                entity.getTargetHeight() != null ? entity.getTargetHeight() : 1920,
                align,
                status,
                params,
                entity.getOutputVideoUrl(),
                entity.getOutputCoverUrl(),
                entity.getDurationSeconds() != null ? entity.getDurationSeconds() : 0.0,
                entity.getFileSizeBytes(),
                entity.getErrorMessage(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now(),
                entity.getFinishedAt()
        );
    }

    public void clear() {
        storage.clear();
    }
}
