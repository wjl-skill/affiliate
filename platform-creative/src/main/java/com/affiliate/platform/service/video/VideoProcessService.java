package com.affiliate.platform.service.video;

import com.affiliate.platform.domain.video.DiskHealthStatus;
import com.affiliate.platform.domain.video.VideoProcessTaskRecord;
import com.affiliate.platform.web.video.dto.VideoProcessRequest;

import java.util.List;
import java.util.Optional;

/**
 * 视频后处理核心应用服务
 */
public interface VideoProcessService {

    /**
     * 提交视频后处理任务 (支持去水印、字幕、片头拼接、CTA动效、裁剪等)
     */
    VideoProcessTaskRecord submitProcessTask(String tenantId, VideoProcessRequest request);

    /**
     * 批量提交视频后处理任务
     */
    List<VideoProcessTaskRecord> submitBatchProcessTasks(String tenantId, List<VideoProcessRequest> requests);

    /**
     * 执行具体任务的处理流水线
     */
    void executeTaskPipeline(String taskId);

    /**
     * 根据业务任务单号查询任务
     */
    Optional<VideoProcessTaskRecord> getTaskByTaskNo(String tenantId, String taskNo);

    /**
     * 根据主键查询任务
     */
    Optional<VideoProcessTaskRecord> getTaskById(String tenantId, String id);

    /**
     * 查询近期处理任务
     */
    List<VideoProcessTaskRecord> listRecentTasks(String tenantId, int limit);

    /**
     * 检查当前系统磁盘容量与高水位健康状态
     */
    DiskHealthStatus checkDiskStatus();
}
