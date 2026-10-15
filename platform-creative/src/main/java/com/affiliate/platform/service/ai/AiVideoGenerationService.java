package com.affiliate.platform.service.ai;

import com.affiliate.platform.domain.ai.*;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.ai.AiGenerateTaskRecordRepository;
import com.affiliate.platform.service.ai.generator.IVideoGenerator;
import com.affiliate.platform.service.ai.generator.VideoGeneratorFactory;
import com.affiliate.platform.service.ai.meta.AiVideoModelMetaService;
import com.affiliate.platform.service.ai.provider.AiVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import com.affiliate.platform.service.ai.provider.dto.ProviderQueryResult;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitResult;
import com.affiliate.platform.service.billing.CreativeAiBillingService;
import com.affiliate.platform.service.billing.impl.CreativeAiBillingServiceImpl;
import com.affiliate.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 视频生成编排应用服务 (AI Video Generation Application Service)
 * <p>
 * 负责入参强校验、任务生命周期持久化、生成器派发、虚拟线程非阻塞轮询、算力预占实扣与跨模块事件发布。
 */
@Service
public class AiVideoGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiVideoGenerationService.class);

    private final AiVideoModelMetaService metaService;
    private final VideoGeneratorFactory generatorFactory;
    private final VideoProviderFactory providerFactory;
    private final AiGenerateTaskRecordRepository taskRepo;
    private final EventPublisher events;
    private final CreativeAiBillingService billingService;

    // Java 21 虚拟线程执行器，用于并发轻量状态轮询
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AiVideoGenerationService(
            AiVideoModelMetaService metaService,
            VideoGeneratorFactory generatorFactory,
            VideoProviderFactory providerFactory,
            AiGenerateTaskRecordRepository taskRepo
    ) {
        this(metaService, generatorFactory, providerFactory, taskRepo, e -> {}, new CreativeAiBillingServiceImpl());
    }

    public AiVideoGenerationService(
            AiVideoModelMetaService metaService,
            VideoGeneratorFactory generatorFactory,
            VideoProviderFactory providerFactory,
            AiGenerateTaskRecordRepository taskRepo,
            EventPublisher events
    ) {
        this(metaService, generatorFactory, providerFactory, taskRepo, events, new CreativeAiBillingServiceImpl());
    }

    @Autowired
    public AiVideoGenerationService(
            AiVideoModelMetaService metaService,
            VideoGeneratorFactory generatorFactory,
            VideoProviderFactory providerFactory,
            AiGenerateTaskRecordRepository taskRepo,
            @Autowired(required = false) EventPublisher events,
            @Autowired(required = false) CreativeAiBillingService billingService
    ) {
        this.metaService = metaService;
        this.generatorFactory = generatorFactory;
        this.providerFactory = providerFactory;
        this.taskRepo = taskRepo;
        this.events = events != null ? events : e -> {};
        this.billingService = billingService != null ? billingService : new CreativeAiBillingServiceImpl();
    }

    /**
     * 提交视频生成任务
     */
    public AiGenerateTaskRecord submitTask(VideoGenerateRequest request, String createdBy) {
        // 1. 模型元数据校验
        metaService.validateRequest(request);

        String tenantId = tenant();
        IVideoGenerator generator = generatorFactory.getGenerator(request.modelCode());
        String providerCode = generator.providerCode();

        String id = "tsk_id_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String taskNo = "tsk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // 2. 创建本地 PENDING 初始任务单
        AiGenerateTaskRecord initialRecord = AiGenerateTaskRecord.createInitial(
                id, tenantId, taskNo, request, providerCode, createdBy != null ? createdBy : "system"
        );
        taskRepo.save(initialRecord);

        // 预估算力费用并冻结预占
        BigDecimal unitPrice = metaService.getModel(request.modelCode())
                .map(AiVideoModelMetaDTO::unitPrice)
                .orElse(BigDecimal.valueOf(0.1500));
        BigDecimal estimatedAmount = unitPrice.multiply(BigDecimal.valueOf(request.durationSeconds())).setScale(4, RoundingMode.HALF_UP);
        billingService.reserveComputeFund(tenantId, taskNo, estimatedAmount);

        try {
            // 3. 委派生成器执行业务参数映射并向供应商发起异步任务提交
            ProviderSubmitResult submitResult = generator.generate(request);

            // 4. 更新为 PROCESSING 状态并持久化 remoteTaskId
            AiGenerateTaskRecord processingRecord = initialRecord.withProcessing(submitResult.remoteTaskId());
            taskRepo.save(processingRecord);

            // 5. 发布任务已提交领域事件
            events.publish(DomainEvent.create(
                    "creative.ai.task.submitted.v1",
                    tenantId,
                    processingRecord.taskNo(),
                    processingRecord
            ));

            log.info("[AiVideoService] Task submitted successfully: taskNo={}, modelCode={}, remoteTaskId={}, estimatedAmount={}",
                    processingRecord.taskNo(), processingRecord.modelCode(), processingRecord.remoteTaskId(), estimatedAmount);

            return processingRecord;
        } catch (Exception e) {
            log.error("[AiVideoService] Failed to submit task to provider: taskNo={}", taskNo, e);
            billingService.releaseComputeHold(tenantId, taskNo);
            AiGenerateTaskRecord failedRecord = initialRecord.withFailed("PROVIDER_SUBMIT_ERROR", e.getMessage());
            taskRepo.save(failedRecord);
            events.publish(DomainEvent.create(
                    "creative.ai.task.failed.v1",
                    tenantId,
                    failedRecord.taskNo(),
                    failedRecord
            ));
            throw new IllegalStateException("Failed to submit AI generation task: " + e.getMessage(), e);
        }
    }

    /**
     * 批量提交视频生成任务
     */
    public List<AiGenerateTaskRecord> submitBatchTasks(List<VideoGenerateRequest> requests, String createdBy) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(req -> submitTask(req, createdBy))
                .toList();
    }

    /**
     * 根据单号查询任务记录
     */
    public Optional<AiGenerateTaskRecord> getTask(String taskNo) {
        return taskRepo.findByTaskNo(tenant(), taskNo);
    }

    /**
     * 取消未完成的生成任务
     */
    public AiGenerateTaskRecord cancelTask(String taskNo) {
        AiGenerateTaskRecord record = getTask(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with taskNo: " + taskNo));

        if (record.status().isTerminal()) {
            return record;
        }

        try {
            AiVideoProviderAdapter provider = providerFactory.getProvider(record.providerCode());
            provider.cancelTask(record.remoteTaskId());
        } catch (Exception e) {
            log.warn("[AiVideoService] Upstream cancel call warning for taskNo: {}", taskNo, e);
        }

        AiGenerateTaskRecord cancelled = record.withCancelled();
        taskRepo.save(cancelled);
        billingService.releaseComputeHold(record.tenantId(), record.taskNo());

        events.publish(DomainEvent.create(
                "creative.ai.task.cancelled.v1",
                tenant(),
                cancelled.taskNo(),
                cancelled
        ));

        return cancelled;
    }

    /**
     * 批量取消未完成的生成任务
     */
    public List<AiGenerateTaskRecord> batchCancelTasks(List<String> taskNos) {
        if (taskNos == null || taskNos.isEmpty()) {
            return List.of();
        }
        return taskNos.stream()
                .map(this::cancelTask)
                .toList();
    }

    /**
     * 轮询所有进行中的任务并更新状态（由调度器或手动触发）
     */
    public int pollProcessingTasks() {
        List<AiGenerateTaskRecord> processingTasks = taskRepo.findByStatus(AiTaskStatus.PROCESSING, 50);
        if (processingTasks.isEmpty()) {
            return 0;
        }

        for (AiGenerateTaskRecord task : processingTasks) {
            // 在 Java 21 虚拟线程中并发执行状态拉取与转存
            virtualExecutor.submit(() -> pollSingleTask(task));
        }

        return processingTasks.size();
    }

    /**
     * 处理单个生成任务的轮询拉取与流式转存
     */
    public void pollSingleTask(AiGenerateTaskRecord task) {
        try {
            AiVideoProviderAdapter provider = providerFactory.getProvider(task.providerCode());
            ProviderQueryResult queryResult = provider.queryTask(task.remoteTaskId());

            if (queryResult.status() == ProviderQueryResult.Status.SUCCEEDED) {
                // 1. 成功拉取到临时视频 URL
                AiGenerateTaskRecord fetchedRecord = task.withSuccessFetched(queryResult.videoUrl());
                taskRepo.save(fetchedRecord);

                // 2. 转存至自身平台私有 S3 OSS (流式上传)
                String ossPermanentUrl = simulateOssTransfer(fetchedRecord.taskNo(), queryResult.videoUrl());

                // 3. 计算实际产生算力费用
                BigDecimal usageQuantity = queryResult.usageQuantity().compareTo(BigDecimal.ZERO) > 0
                        ? queryResult.usageQuantity()
                        : BigDecimal.valueOf(task.durationSeconds());

                BigDecimal unitPrice = metaService.getModel(task.modelCode())
                        .map(AiVideoModelMetaDTO::unitPrice)
                        .orElse(BigDecimal.valueOf(0.1500));

                BigDecimal cost = usageQuantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);

                // 4. 确认扣减算力并写入不可变财务分录
                billingService.confirmComputeDeduction(task.tenantId(), task.taskNo(), cost, task.createdBy(), null, task.modelCode());

                // 5. 标记为终态 SUCCESS
                AiGenerateTaskRecord successRecord = fetchedRecord.withSuccess(ossPermanentUrl, usageQuantity, cost);
                taskRepo.save(successRecord);

                events.publish(DomainEvent.create(
                        "creative.ai.task.succeeded.v1",
                        task.tenantId(),
                        task.taskNo(),
                        successRecord
                ));

                log.info("[AiVideoService] Task completed: taskNo={}, ossUrl={}, cost={}",
                        task.taskNo(), ossPermanentUrl, cost);

            } else if (queryResult.status() == ProviderQueryResult.Status.FAILED) {
                // 释放预占资金
                billingService.releaseComputeHold(task.tenantId(), task.taskNo());

                AiGenerateTaskRecord failedRecord = task.withFailed(
                        queryResult.errorCode() != null ? queryResult.errorCode() : "UPSTREAM_FAILED",
                        queryResult.errorMessage() != null ? queryResult.errorMessage() : "Generation failed upstream"
                );
                taskRepo.save(failedRecord);

                events.publish(DomainEvent.create(
                        "creative.ai.task.failed.v1",
                        task.tenantId(),
                        task.taskNo(),
                        failedRecord
                ));

                log.warn("[AiVideoService] Task failed upstream: taskNo={}, code={}, msg={}",
                        task.taskNo(), queryResult.errorCode(), queryResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[AiVideoService] Error polling task: taskNo={}", task.taskNo(), e);
        }
    }

    /**
     * 查询指定租户最近任务列表
     */
    public List<AiGenerateTaskRecord> listRecentTasks(int limit) {
        return taskRepo.findRecentByTenant(tenant(), limit);
    }

    private String simulateOssTransfer(String taskNo, String rawUrl) {
        // 模拟/构造永久私有 S3/MinIO OSS CDN 目标地址
        return "https://oss.affiliate.com/creatives/ai_video_" + taskNo + ".mp4";
    }

    private static String tenant() {
        return TenantContext.get() == null ? "public" : TenantContext.get();
    }
}
