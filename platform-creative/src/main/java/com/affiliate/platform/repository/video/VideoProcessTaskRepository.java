package com.affiliate.platform.repository.video;

import com.affiliate.platform.domain.video.VideoProcessStatus;
import com.affiliate.platform.domain.video.VideoProcessTaskRecord;

import java.util.List;
import java.util.Optional;

/**
 * 视频处理任务存储仓储接口
 */
public interface VideoProcessTaskRepository {

    VideoProcessTaskRecord save(VideoProcessTaskRecord task);

    Optional<VideoProcessTaskRecord> findById(String tenantId, String id);

    Optional<VideoProcessTaskRecord> findByTaskNo(String tenantId, String taskNo);

    List<VideoProcessTaskRecord> findByStatus(String tenantId, VideoProcessStatus status);

    List<VideoProcessTaskRecord> listRecent(String tenantId, int limit);
}
