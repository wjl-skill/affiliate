package com.affiliate.platform.service.ai.dashboard.impl;

import com.affiliate.platform.domain.ai.AiGenerateTaskRecord;
import com.affiliate.platform.domain.ai.AiTaskStatus;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardRequest;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardResponse;
import com.affiliate.platform.entity.CreativeAiBillingLedgerEntity;
import com.affiliate.platform.repository.ai.AiGenerateTaskRecordRepository;
import com.affiliate.platform.service.ai.dashboard.AiVideoUsageDashboardService;
import com.affiliate.platform.service.billing.CreativeAiBillingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 视频使用量与算力成本大盘分析服务实现
 */
@Service
public class AiVideoUsageDashboardServiceImpl implements AiVideoUsageDashboardService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AiGenerateTaskRecordRepository taskRepo;
    private final CreativeAiBillingService billingService;

    public AiVideoUsageDashboardServiceImpl(
            AiGenerateTaskRecordRepository taskRepo,
            CreativeAiBillingService billingService
    ) {
        this.taskRepo = taskRepo;
        this.billingService = billingService;
    }

    @Override
    public AiVideoUsageDashboardResponse getOverview(String tenantId, AiVideoUsageDashboardRequest request) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        List<AiGenerateTaskRecord> allTasks = taskRepo.findRecentByTenant(effectiveTenant, 1000);
        List<AiGenerateTaskRecord> filtered = filterTasks(allTasks, request);

        long totalCount = filtered.size();
        long successCount = 0;
        long failedCount = 0;
        long processingCount = 0;
        long canceledCount = 0;
        long discardedCount = 0;

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalTokens = BigDecimal.ZERO;
        double totalDuration = 0.0;

        for (AiGenerateTaskRecord task : filtered) {
            AiTaskStatus st = task.status();
            if (st == AiTaskStatus.SUCCESS) {
                successCount++;
                if (task.actualCost() != null) {
                    totalCost = totalCost.add(task.actualCost());
                }
                if (task.usageTokens() != null) {
                    totalTokens = totalTokens.add(task.usageTokens());
                }
                totalDuration += task.durationSeconds();
            } else if (st == AiTaskStatus.FAILED) {
                failedCount++;
            } else if (st == AiTaskStatus.PROCESSING || st == AiTaskStatus.PENDING) {
                processingCount++;
            } else if (st == AiTaskStatus.CANCELLED) {
                canceledCount++;
            } else if (st == AiTaskStatus.DISCARDED) {
                discardedCount++;
            }
        }

        AiVideoUsageDashboardResponse.Overview overview = new AiVideoUsageDashboardResponse.Overview(
                totalCount,
                successCount,
                failedCount,
                processingCount,
                canceledCount,
                discardedCount,
                totalTokens,
                totalDuration,
                totalCost,
                "USD"
        );

        // 1. 每日趋势统计
        Map<String, List<AiGenerateTaskRecord>> dateGroups = filtered.stream()
                .collect(Collectors.groupingBy(t -> {
                    if (t.createdAt() != null) {
                        return LocalDate.ofInstant(t.createdAt(), ZoneId.systemDefault()).format(DATE_FORMATTER);
                    }
                    return LocalDate.now().format(DATE_FORMATTER);
                }));

        List<AiVideoUsageDashboardResponse.TrendRow> trendRows = dateGroups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String date = entry.getKey();
                    List<AiGenerateTaskRecord> list = entry.getValue();
                    long cnt = list.size();
                    long sc = list.stream().filter(t -> t.status() == AiTaskStatus.SUCCESS).count();
                    long fc = list.stream().filter(t -> t.status() == AiTaskStatus.FAILED).count();
                    BigDecimal cost = list.stream()
                            .filter(t -> t.actualCost() != null)
                            .map(AiGenerateTaskRecord::actualCost)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal tokens = list.stream()
                            .filter(t -> t.usageTokens() != null)
                            .map(AiGenerateTaskRecord::usageTokens)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double duration = list.stream()
                            .filter(t -> t.status() == AiTaskStatus.SUCCESS)
                            .mapToDouble(AiGenerateTaskRecord::durationSeconds)
                            .sum();
                    return new AiVideoUsageDashboardResponse.TrendRow(date, cnt, sc, fc, tokens, duration, cost);
                })
                .toList();

        // 2. 模型维度统计
        Map<String, List<AiGenerateTaskRecord>> modelGroups = filtered.stream()
                .collect(Collectors.groupingBy(AiGenerateTaskRecord::modelCode));

        List<AiVideoUsageDashboardResponse.DimensionRow> modelRows = modelGroups.entrySet().stream()
                .map(entry -> buildDimensionRow(entry.getKey(), entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AiVideoUsageDashboardResponse.DimensionRow::cost).reversed())
                .toList();

        // 3. 用户维度统计
        Map<String, List<AiGenerateTaskRecord>> userGroups = filtered.stream()
                .collect(Collectors.groupingBy(t -> t.createdBy() != null ? t.createdBy() : "unknown"));

        List<AiVideoUsageDashboardResponse.DimensionRow> userRows = userGroups.entrySet().stream()
                .map(entry -> buildDimensionRow(entry.getKey(), entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AiVideoUsageDashboardResponse.DimensionRow::cost).reversed())
                .toList();

        // 4. 部门维度统计（结合财务分录）
        List<CreativeAiBillingLedgerEntity> ledgers = billingService.listLedgers(effectiveTenant);
        Map<String, List<CreativeAiBillingLedgerEntity>> deptLedgers = ledgers.stream()
                .collect(Collectors.groupingBy(l -> l.getDepartmentId() != null ? l.getDepartmentId() : "default_dept"));

        List<AiVideoUsageDashboardResponse.DimensionRow> deptRows = deptLedgers.entrySet().stream()
                .map(entry -> {
                    String dept = entry.getKey();
                    List<CreativeAiBillingLedgerEntity> lList = entry.getValue();
                    BigDecimal cost = lList.stream()
                            .map(CreativeAiBillingLedgerEntity::getDeductedAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long count = lList.size();
                    return new AiVideoUsageDashboardResponse.DimensionRow(dept, dept, count, count, BigDecimal.ZERO, 0.0, cost);
                })
                .sorted(Comparator.comparing(AiVideoUsageDashboardResponse.DimensionRow::cost).reversed())
                .toList();

        return new AiVideoUsageDashboardResponse(overview, trendRows, modelRows, userRows, deptRows);
    }

    @Override
    public List<AiGenerateTaskRecord> getDetails(String tenantId, AiVideoUsageDashboardRequest request) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        List<AiGenerateTaskRecord> allTasks = taskRepo.findRecentByTenant(effectiveTenant, 1000);
        List<AiGenerateTaskRecord> filtered = filterTasks(allTasks, request);

        int pageNum = (request != null && request.pageNum() != null) ? request.pageNum() : 1;
        int pageSize = (request != null && request.pageSize() != null) ? request.pageSize() : 20;

        int fromIndex = (pageNum - 1) * pageSize;
        if (fromIndex >= filtered.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        return filtered.subList(fromIndex, toIndex);
    }

    private List<AiGenerateTaskRecord> filterTasks(List<AiGenerateTaskRecord> tasks, AiVideoUsageDashboardRequest request) {
        if (tasks == null || tasks.isEmpty() || request == null) {
            return tasks != null ? tasks : List.of();
        }

        return tasks.stream()
                .filter(t -> {
                    if (request.modelCodes() != null && !request.modelCodes().isEmpty()) {
                        if (!request.modelCodes().contains(t.modelCode())) {
                            return false;
                        }
                    }
                    if (request.taskStatuses() != null && !request.taskStatuses().isEmpty()) {
                        if (!request.taskStatuses().contains(t.status().name())) {
                            return false;
                        }
                    }
                    if (request.createdBy() != null && !request.createdBy().isBlank()) {
                        if (!request.createdBy().equalsIgnoreCase(t.createdBy())) {
                            return false;
                        }
                    }
                    if (request.startDate() != null && !request.startDate().isBlank()) {
                        if (t.createdAt() != null) {
                            String d = LocalDate.ofInstant(t.createdAt(), ZoneId.systemDefault()).format(DATE_FORMATTER);
                            if (d.compareTo(request.startDate()) < 0) {
                                return false;
                            }
                        }
                    }
                    if (request.endDate() != null && !request.endDate().isBlank()) {
                        if (t.createdAt() != null) {
                            String d = LocalDate.ofInstant(t.createdAt(), ZoneId.systemDefault()).format(DATE_FORMATTER);
                            if (d.compareTo(request.endDate()) > 0) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .toList();
    }

    private AiVideoUsageDashboardResponse.DimensionRow buildDimensionRow(
            String key, String name, List<AiGenerateTaskRecord> list
    ) {
        long total = list.size();
        long success = list.stream().filter(t -> t.status() == AiTaskStatus.SUCCESS).count();
        BigDecimal cost = list.stream()
                .filter(t -> t.actualCost() != null)
                .map(AiGenerateTaskRecord::actualCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tokens = list.stream()
                .filter(t -> t.usageTokens() != null)
                .map(AiGenerateTaskRecord::usageTokens)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double duration = list.stream()
                .filter(t -> t.status() == AiTaskStatus.SUCCESS)
                .mapToDouble(AiGenerateTaskRecord::durationSeconds)
                .sum();
        return new AiVideoUsageDashboardResponse.DimensionRow(key, name, total, success, tokens, duration, cost);
    }
}
