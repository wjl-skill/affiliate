package com.affiliate.platform.reporting;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 广告经营多维数据 AI 商业洞察与高管简报生成服务 (AI Report Insight Service)
 */
@Service
public class AiReportInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiReportInsightService.class);

    private final ReportService reportService;
    private final AiOrchestratorService aiOrchestratorService;

    @Autowired
    public AiReportInsightService(
            ReportService reportService,
            @Autowired(required = false) AiOrchestratorService aiOrchestratorService
    ) {
        this.reportService = reportService;
        this.aiOrchestratorService = aiOrchestratorService;
    }

    public AiReportInsightService(ReportService reportService) {
        this(reportService, null);
    }

    public record ExecutiveInsightReport(
            String tenantId,
            String dateRange,
            long totalImpressions,
            long totalClicks,
            long totalConversions,
            BigDecimal totalSpend,
            BigDecimal totalRevenue,
            BigDecimal netProfit,
            double overallRoas,
            String aiExecutiveBriefing,
            Instant generatedAt
    ) {}

    public ExecutiveInsightReport generateExecutiveInsights(String tenantId, LocalDate from, LocalDate to) {
        String tid = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        LocalDate start = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate end = to != null ? to : LocalDate.now();

        List<ReportService.DailyReport> reports = reportService != null
                ? reportService.list(tid, start, end)
                : List.of();

        long impressions = 0;
        long clicks = 0;
        long conversions = 0;
        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;

        for (ReportService.DailyReport r : reports) {
            impressions += r.impressions();
            clicks += r.clicks();
            conversions += r.conversions();
            spend = spend.add(r.spend() != null ? r.spend() : BigDecimal.ZERO);
            revenue = revenue.add(r.revenue() != null ? r.revenue() : BigDecimal.ZERO);
        }

        BigDecimal netProfit = revenue.subtract(spend);
        double roas = spend.compareTo(BigDecimal.ZERO) > 0
                ? revenue.doubleValue() / spend.doubleValue()
                : (revenue.compareTo(BigDecimal.ZERO) > 0 ? 99.0 : 0.0);

        String dateRangeStr = start + " ~ " + end;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("impressions", impressions);
        snapshot.put("clicks", clicks);
        snapshot.put("conversions", conversions);
        snapshot.put("spend", spend);
        snapshot.put("revenue", revenue);
        snapshot.put("profit", netProfit);
        snapshot.put("roas", String.format("%.2f", roas));

        String briefing;
        if (aiOrchestratorService != null) {
            try {
                briefing = aiOrchestratorService.synthesizeReportInsightsWithAi(tid, dateRangeStr, snapshot);
            } catch (Exception e) {
                log.warn("[AiReportInsight] AI report synthesis fallback: {}", e.getMessage());
                briefing = generateHeuristicBriefing(dateRangeStr, snapshot, roas);
            }
        } else {
            briefing = generateHeuristicBriefing(dateRangeStr, snapshot, roas);
        }

        return new ExecutiveInsightReport(
                tid,
                dateRangeStr,
                impressions,
                clicks,
                conversions,
                spend,
                revenue,
                netProfit,
                roas,
                briefing,
                Instant.now()
        );
    }

    private String generateHeuristicBriefing(String dateRange, Map<String, Object> metrics, double roas) {
        return String.format(
                "【AI 自动化经营分析与商业洞察摘要 (%s)】\n" +
                "1. 核心大盘：周期内录得曝光 %s 次，产生点击 %s 次，有效转化 %s 笔。\n" +
                "2. 财务回报：总消耗 $%s，总收入 $%s，毛利净额 $%s，ROAS 达到 %.2f。\n" +
                "3. 策略建议：%s",
                dateRange,
                metrics.get("impressions"),
                metrics.get("clicks"),
                metrics.get("conversions"),
                metrics.get("spend"),
                metrics.get("revenue"),
                metrics.get("profit"),
                roas,
                roas >= 1.2
                        ? "当前整体回报率处于健康上升通道，建议对头部高 ROAS 广告组实施 15% 预算倾斜扩量。"
                        : "整体投产比偏低，建议加强长尾低效点位过滤并借助 AI 素材 Agent 迭代黄金 Hook 视频素材。"
        );
    }
}
