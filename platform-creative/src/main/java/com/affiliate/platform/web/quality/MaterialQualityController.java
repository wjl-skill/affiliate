package com.affiliate.platform.web.quality;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.quality.MaterialQualityResult;
import com.affiliate.platform.service.quality.MaterialQualityService;
import com.affiliate.platform.service.quality.MaterialQualityTagJobService;
import com.affiliate.platform.tenant.TenantContext;
import com.affiliate.platform.web.quality.dto.BenchmarkSaveRequest;
import com.affiliate.platform.web.quality.dto.MaterialEvaluateRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 素材质量评估与大盘基准 REST API 控制器
 * <p>
 * 提供投放效果评估评级、大盘动态基准线查询/同步及历史评级回溯。
 */
@RestController
@RequestMapping("/api/v1/creative/quality")
public class MaterialQualityController {

    private final MaterialQualityService qualityService;
    private final MaterialQualityTagJobService jobService;

    public MaterialQualityController(MaterialQualityService qualityService, MaterialQualityTagJobService jobService) {
        this.qualityService = qualityService;
        this.jobService = jobService;
    }

    /**
     * 提交素材 7 日投放事实指标并执行质量评级
     * POST /api/v1/creative/quality/evaluate
     */
    @PostMapping("/evaluate")
    @ResponseStatus(HttpStatus.OK)
    public MaterialQualityResult evaluate(@Valid @RequestBody MaterialEvaluateRequest request) {
        return qualityService.evaluateAndRecord(request.toDomain());
    }

    /**
     * 查询指定素材的最新评级推荐与操作建议
     * GET /api/v1/creative/quality/recommend?creativeId=cr_10086
     */
    @GetMapping("/recommend")
    public MaterialQualityResult recommend(@RequestParam String creativeId) {
        return qualityService.getLatestResult(creativeId)
                .orElseThrow(() -> new IllegalArgumentException("No quality evaluation record found for creativeId: " + creativeId));
    }

    /**
     * 获取最新一期全盘 7 日动态基准线
     * GET /api/v1/creative/quality/benchmark/latest
     */
    @GetMapping("/benchmark/latest")
    public MaterialBenchmark7D getLatestBenchmark() {
        return qualityService.getLatestBenchmark();
    }

    /**
     * 保存或更新 7 日大盘基准线 (通常由数仓定时调度器同步)
     * POST /api/v1/creative/quality/benchmark
     */
    @PostMapping("/benchmark")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialBenchmark7D saveBenchmark(@Valid @RequestBody BenchmarkSaveRequest request) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        return qualityService.saveBenchmark(request.toDomain(tenantId));
    }

    /**
     * 查询指定素材的历史评级记录
     * GET /api/v1/creative/quality/history?creativeId=cr_10086&limit=10
     */
    @GetMapping("/history")
    public List<MaterialQualityResult> getHistory(
            @RequestParam String creativeId,
            @RequestParam(defaultValue = "10") int limit) {
        return qualityService.getHistory(creativeId, limit);
    }

    /**
     * 按质量等级与日期筛选素材列表
     * GET /api/v1/creative/quality/list?level=STAR&date=2026-10-14
     */
    @GetMapping("/list")
    public List<MaterialQualityResult> listByLevel(
            @RequestParam(required = false) MaterialQualityLevel level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return qualityService.listByLevel(level, date);
    }

    /**
     * 批量刷新多素材评估标签
     * POST /api/v1/creative/quality/job/batch-refresh
     */
    @PostMapping("/job/batch-refresh")
    public MaterialQualityTagJobService.JobRefreshSummary batchRefresh(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            @RequestBody List<com.affiliate.platform.domain.quality.MaterialPerformance7D> performances) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        return jobService.batchRefresh(tenantId, statDate, performances);
    }

    /**
     * 触发每日巡检定时刷新任务
     * POST /api/v1/creative/quality/job/daily-refresh
     */
    @PostMapping("/job/daily-refresh")
    public MaterialQualityTagJobService.JobRefreshSummary executeDailyRefresh() {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        return jobService.executeDailyScheduledRefresh(tenantId);
    }
}
