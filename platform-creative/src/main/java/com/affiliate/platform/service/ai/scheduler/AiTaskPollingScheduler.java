package com.affiliate.platform.service.ai.scheduler;

import com.affiliate.platform.domain.ai.AiGenerateTaskRecord;
import com.affiliate.platform.domain.ai.AiTaskStatus;
import com.affiliate.platform.lock.DistributedLock;
import com.affiliate.platform.lock.RedisDistributedLock;
import com.affiliate.platform.repository.ai.AiGenerateTaskRecordRepository;
import com.affiliate.platform.service.ai.AiVideoGenerationService;
import com.affiliate.platform.service.billing.CreativeAiBillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AI 视频生成任务定时轮询与生命周期回收调度器 (采用分布式锁加固防重)
 * <p>
 * 1. 自动轮询处理中的 AI 视频生成任务并更新结果；
 * 2. 自动超时熔断：处理时间超过 2 小时的死锁/挂起任务自动标记失败并解冻预占算力；
 * 3. 自动归档废弃：拉取成功但超过 5 天未落库确认的任务标记 DISCARDED。
 */
@Component
public class AiTaskPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiTaskPollingScheduler.class);

    private static final Duration STALE_PROCESSING_TIMEOUT = Duration.ofHours(2);
    private static final Duration SUCCESS_FETCHED_DISCARD_TIMEOUT = Duration.ofDays(5);

    private final AiVideoGenerationService generationService;
    private final AiGenerateTaskRecordRepository taskRepo;
    private final CreativeAiBillingService billingService;
    private final DistributedLock distributedLock;

    public AiTaskPollingScheduler(
            AiVideoGenerationService generationService,
            AiGenerateTaskRecordRepository taskRepo,
            CreativeAiBillingService billingService
    ) {
        this(generationService, taskRepo, billingService, null);
    }

    @Autowired
    public AiTaskPollingScheduler(
            AiVideoGenerationService generationService,
            AiGenerateTaskRecordRepository taskRepo,
            CreativeAiBillingService billingService,
            @Autowired(required = false) DistributedLock distributedLock
    ) {
        this.generationService = generationService;
        this.taskRepo = taskRepo;
        this.billingService = billingService;
        this.distributedLock = distributedLock != null ? distributedLock : new RedisDistributedLock(null);
    }

    /**
     * 定时轮询调度：每 10 秒扫描并拉取所有进行中 (PROCESSING) 任务的状态
     */
    @Scheduled(fixedDelay = 10000)
    public int pollPendingAiTasks() {
        String lockKey = "lock:ai:polling";
        var lockOpt = distributedLock.tryAcquire(lockKey, Duration.ofSeconds(9));
        if (lockOpt.isEmpty()) {
            log.debug("[AiTaskPollingScheduler] Another instance holds lock {}, skipping polling run", lockKey);
            return 0;
        }

        try (var lock = lockOpt.get()) {
            int polled = generationService.pollProcessingTasks();
            if (polled > 0) {
                log.info("[AiTaskPollingScheduler] Polled {} active AI tasks", polled);
            }
            return polled;
        } catch (Exception e) {
            log.error("[AiTaskPollingScheduler] Error polling AI video tasks: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 任务生命周期巡检：每 5 分钟扫描僵死/超时与过期任务
     */
    @Scheduled(fixedDelay = 300000)
    public int cleanupStaleTasks() {
        String lockKey = "lock:ai:cleanup_stale";
        var lockOpt = distributedLock.tryAcquire(lockKey, Duration.ofMinutes(4));
        if (lockOpt.isEmpty()) {
            log.debug("[AiTaskPollingScheduler] Another instance holds lock {}, skipping stale cleanup run", lockKey);
            return 0;
        }

        try (var lock = lockOpt.get()) {
            int cleaned = 0;
            Instant now = Instant.now();

            try {
                // 1. 扫描 PROCESSING 超过 2 小时的任务，熔断失败并解冻算力
                List<AiGenerateTaskRecord> processing = taskRepo.findByStatus(AiTaskStatus.PROCESSING, 100);
                for (AiGenerateTaskRecord t : processing) {
                    if (t.createdAt() != null && Duration.between(t.createdAt(), now).compareTo(STALE_PROCESSING_TIMEOUT) > 0) {
                        log.warn("[AiTaskPollingScheduler] Task timeout exceeded, aborting: taskNo={}, createdAt={}",
                                t.taskNo(), t.createdAt());
                        AiGenerateTaskRecord timedOut = t.withFailed("TASK_TIMEOUT_EXCEEDED", "Task processing exceeded max allowed duration of 2 hours");
                        taskRepo.save(timedOut);
                        billingService.releaseComputeHold(t.tenantId(), t.taskNo());
                        cleaned++;
                    }
                }

                // 2. 扫描 SUCCESS_FETCHED 超过 5 天的任务，标记 DISCARDED
                List<AiGenerateTaskRecord> fetched = taskRepo.findByStatus(AiTaskStatus.SUCCESS_FETCHED, 100);
                for (AiGenerateTaskRecord t : fetched) {
                    if (t.createdAt() != null && Duration.between(t.createdAt(), now).compareTo(SUCCESS_FETCHED_DISCARD_TIMEOUT) > 0) {
                        log.info("[AiTaskPollingScheduler] Auto discarding unconfirmed fetched task: taskNo={}", t.taskNo());
                        AiGenerateTaskRecord discarded = t.withDiscarded("Task fetched video expired after 5 days without confirmation");
                        taskRepo.save(discarded);
                        cleaned++;
                    }
                }
            } catch (Exception e) {
                log.error("[AiTaskPollingScheduler] Error during stale task cleanup: {}", e.getMessage(), e);
            }

            return cleaned;
        }
    }

    /**
     * 优雅停机钩子：若有长期守护任务可在此清理
     */
    public void shutdown() {
        log.info("[AiTaskPollingScheduler] Shutting down scheduler gracefully");
    }
}
