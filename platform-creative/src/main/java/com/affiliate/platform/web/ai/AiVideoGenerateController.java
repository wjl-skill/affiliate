package com.affiliate.platform.web.ai;

import com.affiliate.platform.domain.ai.AiGenerateTaskRecord;
import com.affiliate.platform.domain.ai.AiVideoModelMetaDTO;
import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardRequest;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardResponse;
import com.affiliate.platform.service.ai.AiVideoGenerationService;
import com.affiliate.platform.service.ai.dashboard.AiVideoUsageDashboardService;
import com.affiliate.platform.service.ai.meta.AiVideoModelMetaService;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 视频多模型生成 REST 控制器
 * <p>
 * 提供模型元数据动态查询、异步生成任务提交、状态轮询、取消及多维用量大盘统计。
 */
@RestController
@RequestMapping("/api/v1/creative/ai")
public class AiVideoGenerateController {

    private final AiVideoGenerationService generationService;
    private final AiVideoModelMetaService metaService;
    private final AiVideoUsageDashboardService dashboardService;

    public AiVideoGenerateController(
            AiVideoGenerationService generationService,
            AiVideoModelMetaService metaService
    ) {
        this(generationService, metaService, null);
    }

    @Autowired
    public AiVideoGenerateController(
            AiVideoGenerationService generationService,
            AiVideoModelMetaService metaService,
            @Autowired(required = false) AiVideoUsageDashboardService dashboardService
    ) {
        this.generationService = generationService;
        this.metaService = metaService;
        this.dashboardService = dashboardService;
    }

    /**
     * 获取全量可用模型元数据列表 (用于前端动态渲染参数表单)
     * GET /api/v1/creative/ai/models
     */
    @GetMapping("/models")
    public List<AiVideoModelMetaDTO> listModels() {
        return metaService.listAvailableModels();
    }

    /**
     * 获取指定模型元数据详情
     * GET /api/v1/creative/ai/models/{modelCode}
     */
    @GetMapping("/models/{modelCode}")
    public AiVideoModelMetaDTO getModel(@PathVariable String modelCode) {
        return metaService.getModel(modelCode)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelCode));
    }

    /**
     * 提交视频生成任务
     * POST /api/v1/creative/ai/generate
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiGenerateTaskRecord generate(@Valid @RequestBody VideoGenerateRequest request) {
        return generationService.submitTask(request, "current_user");
    }

    /**
     * 批量提交视频生成任务
     * POST /api/v1/creative/ai/generate/batch
     */
    @PostMapping("/generate/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<AiGenerateTaskRecord> batchGenerate(@Valid @RequestBody List<VideoGenerateRequest> requests) {
        return generationService.submitBatchTasks(requests, "current_user");
    }

    /**
     * 根据单号查询任务状态
     * GET /api/v1/creative/ai/tasks/{taskNo}
     */
    @GetMapping("/tasks/{taskNo}")
    public AiGenerateTaskRecord getTask(@PathVariable String taskNo) {
        return generationService.getTask(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with taskNo: " + taskNo));
    }

    /**
     * 取消未完成的生成任务
     * POST /api/v1/creative/ai/tasks/{taskNo}/cancel
     */
    @PostMapping("/tasks/{taskNo}/cancel")
    public AiGenerateTaskRecord cancelTask(@PathVariable String taskNo) {
        return generationService.cancelTask(taskNo);
    }

    /**
     * 批量取消未完成的生成任务
     * POST /api/v1/creative/ai/tasks/batch-cancel
     */
    @PostMapping("/tasks/batch-cancel")
    public List<AiGenerateTaskRecord> batchCancelTasks(@RequestBody List<String> taskNos) {
        return generationService.batchCancelTasks(taskNos);
    }

    /**
     * 触发异步任务批量轮询扫描 (供监控中心或定时任务调用)
     * POST /api/v1/creative/ai/tasks/poll
     */
    @PostMapping("/tasks/poll")
    public int pollTasks() {
        return generationService.pollProcessingTasks();
    }

    /**
     * 查询租户最近任务列表
     * GET /api/v1/creative/ai/tasks/recent?limit=20
     */
    @GetMapping("/tasks/recent")
    public List<AiGenerateTaskRecord> listRecentTasks(@RequestParam(defaultValue = "20") int limit) {
        return generationService.listRecentTasks(limit);
    }

    /**
     * 查询 AI 视频算力与使用量分析大盘概览
     * GET /api/v1/creative/ai/dashboard/overview
     */
    @GetMapping("/dashboard/overview")
    public AiVideoUsageDashboardResponse getDashboardOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) List<String> modelCodes,
            @RequestParam(required = false) List<String> taskStatuses,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String createdBy
    ) {
        String tenant = TenantContext.get() != null ? TenantContext.get() : "public";
        AiVideoUsageDashboardRequest req = new AiVideoUsageDashboardRequest(
                startDate, endDate, modelCodes, taskStatuses, departmentId, createdBy, 1, 20
        );
        if (dashboardService != null) {
            return dashboardService.getOverview(tenant, req);
        }
        return new AiVideoUsageDashboardResponse(
                new AiVideoUsageDashboardResponse.Overview(0, 0, 0, 0, 0, 0, java.math.BigDecimal.ZERO, 0.0, java.math.BigDecimal.ZERO, "USD"),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    /**
     * 分页查询 AI 视频生成任务明细
     * GET /api/v1/creative/ai/dashboard/details
     */
    @GetMapping("/dashboard/details")
    public List<AiGenerateTaskRecord> getDashboardDetails(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) List<String> modelCodes,
            @RequestParam(required = false) List<String> taskStatuses,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        String tenant = TenantContext.get() != null ? TenantContext.get() : "public";
        AiVideoUsageDashboardRequest req = new AiVideoUsageDashboardRequest(
                startDate, endDate, modelCodes, taskStatuses, departmentId, createdBy, pageNum, pageSize
        );
        if (dashboardService != null) {
            return dashboardService.getDetails(tenant, req);
        }
        return List.of();
    }
}
