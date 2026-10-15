package com.affiliate.platform.web.video;

import com.affiliate.platform.domain.video.AutoStitchConfig;
import com.affiliate.platform.domain.video.AutoStitchStatus;
import com.affiliate.platform.domain.video.DiskHealthStatus;
import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessTaskRecord;
import com.affiliate.platform.service.video.VideoProcessService;
import com.affiliate.platform.service.video.probe.MediaProbeService;
import com.affiliate.platform.service.video.scheduler.AutoStitchScheduler;
import com.affiliate.platform.tenant.TenantContext;
import com.affiliate.platform.web.video.dto.AutoStitchCreateRequest;
import com.affiliate.platform.web.video.dto.AutoStitchTriggerResult;
import com.affiliate.platform.web.video.dto.VideoProbeRequest;
import com.affiliate.platform.web.video.dto.VideoProcessRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频后处理与混剪调度 REST 控制器
 */
@RestController
@RequestMapping("/api/v1/creative/video")
public class VideoProcessController {

    private final VideoProcessService videoProcessService;
    private final MediaProbeService probeService;
    private final AutoStitchScheduler autoStitchScheduler;

    public VideoProcessController(
            VideoProcessService videoProcessService,
            MediaProbeService probeService,
            AutoStitchScheduler autoStitchScheduler
    ) {
        this.videoProcessService = videoProcessService;
        this.probeService = probeService;
        this.autoStitchScheduler = autoStitchScheduler;
    }

    /**
     * 探针提取视频元数据
     * POST /api/v1/creative/video/probe
     */
    @PostMapping("/probe")
    public VideoProbeResult probe(@Valid @RequestBody VideoProbeRequest request) {
        return probeService.probe(request.videoUrlOrPath());
    }

    /**
     * 提交单视频处理任务 (裁剪、去水印、压字幕、CTA贴片、片头硬拼接等)
     * POST /api/v1/creative/video/process
     */
    @PostMapping("/process")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoProcessTaskRecord submitProcessTask(@Valid @RequestBody VideoProcessRequest request) {
        String tenant = TenantContext.get();
        return videoProcessService.submitProcessTask(tenant, request);
    }

    /**
     * 批量提交视频后处理任务
     * POST /api/v1/creative/video/process/batch
     */
    @PostMapping("/process/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<VideoProcessTaskRecord> submitBatchProcessTasks(@Valid @RequestBody List<VideoProcessRequest> requests) {
        String tenant = TenantContext.get();
        return videoProcessService.submitBatchProcessTasks(tenant, requests);
    }

    /**
     * 查询单个任务详情
     * GET /api/v1/creative/video/tasks/{taskNo}
     */
    @GetMapping("/tasks/{taskNo}")
    public VideoProcessTaskRecord getTask(@PathVariable String taskNo) {
        String tenant = TenantContext.get();
        return videoProcessService.getTaskByTaskNo(tenant, taskNo)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskNo));
    }

    /**
     * 查询近期视频处理任务列表
     * GET /api/v1/creative/video/tasks
     */
    @GetMapping("/tasks")
    public List<VideoProcessTaskRecord> listRecentTasks(@RequestParam(defaultValue = "20") int limit) {
        String tenant = TenantContext.get();
        return videoProcessService.listRecentTasks(tenant, limit);
    }

    /**
     * 创建定时批量混剪配置
     * POST /api/v1/creative/video/auto-stitch/configs
     */
    @PostMapping("/auto-stitch/configs")
    @ResponseStatus(HttpStatus.CREATED)
    public AutoStitchConfig createAutoStitchConfig(@Valid @RequestBody AutoStitchCreateRequest request) {
        String tenant = TenantContext.get();
        return autoStitchScheduler.createConfig(tenant, request);
    }

    /**
     * 查询批量混剪配置详情
     * GET /api/v1/creative/video/auto-stitch/configs/{id}
     */
    @GetMapping("/auto-stitch/configs/{id}")
    public AutoStitchConfig getAutoStitchConfig(@PathVariable String id) {
        String tenant = TenantContext.get();
        return autoStitchScheduler.findConfigById(tenant, id)
                .orElseThrow(() -> new IllegalArgumentException("Auto stitch config not found: " + id));
    }

    /**
     * 查询租户下所有混剪配置
     * GET /api/v1/creative/video/auto-stitch/configs
     */
    @GetMapping("/auto-stitch/configs")
    public List<AutoStitchConfig> listAutoStitchConfigs() {
        String tenant = TenantContext.get();
        return autoStitchScheduler.listConfigs(tenant);
    }

    /**
     * 立即触发批量混剪配置生成
     * POST /api/v1/creative/video/auto-stitch/configs/{id}/trigger
     */
    @PostMapping("/auto-stitch/configs/{id}/trigger")
    public AutoStitchTriggerResult triggerAutoStitchConfig(@PathVariable String id) {
        String tenant = TenantContext.get();
        return autoStitchScheduler.triggerBatchStitch(tenant, id);
    }

    /**
     * 删除批量混剪配置
     * DELETE /api/v1/creative/video/auto-stitch/configs/{id}
     */
    @DeleteMapping("/auto-stitch/configs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAutoStitchConfig(@PathVariable String id) {
        String tenant = TenantContext.get();
        boolean deleted = autoStitchScheduler.deleteConfig(tenant, id);
        if (!deleted) {
            throw new IllegalArgumentException("Auto stitch config not found or already deleted: " + id);
        }
    }

    /**
     * 更新批量混剪配置状态 (如取消 CANCELLED 或激活 PENDING)
     * PUT /api/v1/creative/video/auto-stitch/configs/{id}/status
     */
    @PutMapping("/auto-stitch/configs/{id}/status")
    public AutoStitchConfig updateAutoStitchConfigStatus(
            @PathVariable String id,
            @RequestParam AutoStitchStatus status
    ) {
        String tenant = TenantContext.get();
        return autoStitchScheduler.updateConfigStatus(tenant, id, status);
    }

    /**
     * 宿主机磁盘与临时沙箱高水位健康状态
     * GET /api/v1/creative/video/disk-status
     */
    @GetMapping("/disk-status")
    public DiskHealthStatus getDiskStatus() {
        return videoProcessService.checkDiskStatus();
    }
}
