package com.affiliate.platform.web;

import com.affiliate.platform.reporting.CohortAnalysisService;
import com.affiliate.platform.reporting.PivotAggregationEngine;
import com.affiliate.platform.reporting.ReportService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 广告报表多维统计 REST 控制器 (Reporting REST Controller)
 * <p>
 * 提供按租户隔离的日期范围多维报表检索、多维动态透视下钻及 Cohort 留存衰减分析端点。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;
    private final PivotAggregationEngine pivotEngine;
    private final CohortAnalysisService cohortService;

    @Autowired
    public ReportController(
            ReportService service,
            @Autowired(required = false) PivotAggregationEngine pivotEngine,
            @Autowired(required = false) CohortAnalysisService cohortService
    ) {
        this.service = service;
        this.pivotEngine = pivotEngine != null ? pivotEngine : new PivotAggregationEngine();
        this.cohortService = cohortService != null ? cohortService : new CohortAnalysisService();
    }

    /**
     * 按日期区间检索当前租户的多维效果与资金日报表
     * GET /api/v1/reports/daily?from=2026-08-01&to=2026-09-02
     */
    @GetMapping("/daily")
    public List<ReportService.DailyReport> daily(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.list(TenantContext.required(), from, to);
    }

    /**
     * 多维动态切片与透视聚合分析
     * POST /api/v1/reports/pivot
     */
    @PostMapping("/pivot")
    public List<PivotAggregationEngine.PivotRow> pivot(@RequestBody PivotRequest req) {
        return pivotEngine.aggregate(
                req.records(),
                req.groupBy(),
                req.filters(),
                req.sortBy(),
                req.descending() == null || req.descending()
        );
    }

    /**
     * 留存率与 LTV 衰减 Cohort 分析
     * GET /api/v1/reports/cohort?from=2026-08-01&to=2026-09-02
     */
    @GetMapping("/cohort")
    public CohortAnalysisService.CohortMatrix cohort(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return cohortService.generateMatrix(from, to);
    }

    /**
     * 透视聚合请求参数
     */
    public record PivotRequest(
            List<PivotAggregationEngine.PivotRecord> records,
            List<String> groupBy,
            Map<String, String> filters,
            String sortBy,
            Boolean descending
    ) {}
}
