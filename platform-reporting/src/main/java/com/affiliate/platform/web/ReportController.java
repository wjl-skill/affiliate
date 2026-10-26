package com.affiliate.platform.web;

import com.affiliate.platform.reporting.CohortAnalysisService;
import com.affiliate.platform.reporting.PivotAggregationEngine;
import com.affiliate.platform.reporting.ReportService;
import com.affiliate.platform.reporting.olap.OlapReportingEngine;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 广告报表多维统计 REST 控制器 (Reporting REST Controller)
 * <p>
 * 提供按租户隔离的日期范围多维报表检索、多维动态透视下钻及 Cohort 留存衰减分析端点。
 * 全面集成 Spring Security 方法级细粒度权限管控 (@PreAuthorize)。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;
    private final PivotAggregationEngine pivotEngine;
    private final CohortAnalysisService cohortService;
    private final OlapReportingEngine olapEngine;
    private final com.affiliate.platform.reporting.AiReportInsightService aiReportInsightService;

    @Autowired
    public ReportController(
            ReportService service,
            @Autowired(required = false) PivotAggregationEngine pivotEngine,
            @Autowired(required = false) CohortAnalysisService cohortService,
            @Autowired(required = false) OlapReportingEngine olapEngine,
            @Autowired(required = false) com.affiliate.platform.reporting.AiReportInsightService aiReportInsightService
    ) {
        this.service = service;
        this.pivotEngine = pivotEngine != null ? pivotEngine : new PivotAggregationEngine();
        this.cohortService = cohortService != null ? cohortService : new CohortAnalysisService();
        this.olapEngine = olapEngine != null ? olapEngine : new OlapReportingEngine();
        this.aiReportInsightService = aiReportInsightService != null ? aiReportInsightService : new com.affiliate.platform.reporting.AiReportInsightService(service);
    }

    /**
     * 按日期区间检索当前租户的多维效果与资金日报表
     * GET /api/v1/reports/daily?from=2026-08-01&to=2026-09-02
     */
    @GetMapping("/daily")
    @PreAuthorize("@perm.has('report:read')")
    public List<ReportService.DailyReport> daily(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.list(TenantContext.required(), from, to);
    }

    /**
     * 多维动态切片与透视聚合分析
     * POST /api/v1/reports/pivot
     */
    @PostMapping("/pivot")
    @PreAuthorize("@perm.has('report:read')")
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
     * 双引擎 OLAP 高性能多维聚合查询
     * GET /api/v1/reports/olap?from=2026-08-01&to=2026-09-02&groupBy=offerId
     */
    @GetMapping("/olap")
    @PreAuthorize("@perm.has('report:read')")
    public List<OlapReportingEngine.OlapAggregationRow> olap(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false, defaultValue = "offerId") String groupBy
    ) {
        String tenant = TenantContext.get() != null ? TenantContext.get() : "default";
        OlapReportingEngine engine = olapEngine != null ? olapEngine : new OlapReportingEngine();
        return engine.queryAggregation(tenant, from, to, groupBy);
    }

    /**
     * 留存率与 LTV 衰减 Cohort 分析
     * GET /api/v1/reports/cohort?from=2026-08-01&to=2026-09-02
     */
    @GetMapping("/cohort")
    @PreAuthorize("@perm.has('report:read')")
    public CohortAnalysisService.CohortMatrix cohort(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return cohortService.generateMatrix(from, to);
    }

    /**
     * AI 商业数据多维洞察与高管经营分析报告
     * GET /api/v1/reports/ai-insights?from=2026-08-01&to=2026-09-02
     */
    @GetMapping("/ai-insights")
    @PreAuthorize("@perm.has('report:read')")
    public com.affiliate.platform.reporting.AiReportInsightService.ExecutiveInsightReport aiInsights(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        String tenant = TenantContext.get() != null ? TenantContext.get() : "default";
        return aiReportInsightService.generateExecutiveInsights(tenant, from, to);
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
