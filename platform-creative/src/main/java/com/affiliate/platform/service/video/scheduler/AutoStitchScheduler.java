package com.affiliate.platform.service.video.scheduler;

import com.affiliate.platform.domain.video.AutoStitchConfig;
import com.affiliate.platform.domain.video.AutoStitchStatus;
import com.affiliate.platform.domain.video.VideoProcessTaskRecord;
import com.affiliate.platform.domain.video.VideoProcessType;
import com.affiliate.platform.repository.video.AutoStitchConfigRepository;
import com.affiliate.platform.service.video.AutoStitchService;
import com.affiliate.platform.service.video.VideoProcessService;
import com.affiliate.platform.web.video.dto.AutoStitchCreateRequest;
import com.affiliate.platform.web.video.dto.AutoStitchTriggerResult;
import com.affiliate.platform.web.video.dto.VideoProcessRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.awt.Dimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 批量混剪调度器：负责笛卡尔积交叉组合生成、定时触发与单次消费幂等保证
 */
@Service
public class AutoStitchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoStitchScheduler.class);

    private final AutoStitchConfigRepository configRepo;
    private final AutoStitchService autoStitchService;
    private final VideoProcessService videoProcessService;

    public AutoStitchScheduler(
            AutoStitchConfigRepository configRepo,
            AutoStitchService autoStitchService,
            VideoProcessService videoProcessService
    ) {
        this.configRepo = configRepo;
        this.autoStitchService = autoStitchService;
        this.videoProcessService = videoProcessService;
    }

    /**
     * 创建批量混剪配置
     */
    public AutoStitchConfig createConfig(String tenantId, AutoStitchCreateRequest request) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        AutoStitchConfig config = AutoStitchConfig.create(
                effectiveTenant,
                request.configName(),
                request.introVideoUrls(),
                request.bodyVideoUrls(),
                request.targetAspectRatio(),
                request.alignMode(),
                request.ctaConfig(),
                request.scheduledTime()
        );
        return configRepo.save(config);
    }

    public Optional<AutoStitchConfig> findConfigById(String tenantId, String id) {
        return configRepo.findById(tenantId != null ? tenantId : "public", id);
    }

    public List<AutoStitchConfig> listConfigs(String tenantId) {
        return configRepo.listByTenant(tenantId != null ? tenantId : "public");
    }

    /**
     * 删除批量混剪配置
     */
    public boolean deleteConfig(String tenantId, String id) {
        return configRepo.deleteById(tenantId != null ? tenantId : "public", id);
    }

    /**
     * 更新批量混剪配置状态 (如取消 CANCELLED 或激活 PENDING)
     */
    public AutoStitchConfig updateConfigStatus(String tenantId, String id, AutoStitchStatus status) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        AutoStitchConfig config = configRepo.findById(effectiveTenant, id)
                .orElseThrow(() -> new IllegalArgumentException("Auto stitch config not found: " + id));
        AutoStitchConfig updated = config.withStatus(status);
        return configRepo.save(updated);
    }

    /**
     * 立即触发批量混剪配置执行（笛卡尔积交叉生成子任务）
     */
    public synchronized AutoStitchTriggerResult triggerBatchStitch(String tenantId, String configId) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        AutoStitchConfig config = configRepo.findById(effectiveTenant, configId)
                .orElseThrow(() -> new IllegalArgumentException("Auto stitch config not found: " + configId));

        if (config.status() != AutoStitchStatus.PENDING) {
            log.info("Auto stitch config {} has status {}, skipping execution", configId, config.status());
            return new AutoStitchTriggerResult(configId, 0, List.of());
        }

        // 1. 计算目标分辨率
        Dimension targetDim = autoStitchService.resolveTargetDimension(config.targetAspectRatio());

        // 2. 生成 N x M 笛卡尔积组合
        List<Map<String, String>> pairs = autoStitchService.generateCartesianPairs(
                config.introVideoUrls(),
                config.bodyVideoUrls()
        );

        List<String> generatedTaskNos = new ArrayList<>();

        for (Map<String, String> pair : pairs) {
            String intro = pair.get("introVideoUrl");
            String body = pair.get("bodyVideoUrl");

            VideoProcessRequest procReq = new VideoProcessRequest(
                    VideoProcessType.INTRO_STITCH,
                    body,
                    intro,
                    targetDim.width,
                    targetDim.height,
                    config.alignMode(),
                    config.ctaConfig()
            );

            VideoProcessTaskRecord task = videoProcessService.submitProcessTask(effectiveTenant, procReq);
            generatedTaskNos.add(task.taskNo());
        }

        // 3. 标记配置为已执行
        configRepo.save(config.markExecuted());
        log.info("Batch auto stitch config {} triggered successfully, generated {} tasks",
                configId, generatedTaskNos.size());

        return new AutoStitchTriggerResult(configId, generatedTaskNos.size(), generatedTaskNos);
    }

    /**
     * 定时轮询调度：每 60 秒检查计划时间到期的批量混剪配置
     */
    @Scheduled(fixedDelay = 60000)
    public int pollAndTriggerScheduledConfigs() {
        Instant now = Instant.now();
        List<AutoStitchConfig> pending = configRepo.findPendingSchedules(now);
        int triggeredCount = 0;

        for (AutoStitchConfig config : pending) {
            try {
                triggerBatchStitch(config.tenantId(), config.id());
                triggeredCount++;
            } catch (Exception e) {
                log.error("Error triggering scheduled auto stitch config {}: {}", config.id(), e.getMessage());
            }
        }

        return triggeredCount;
    }
}
