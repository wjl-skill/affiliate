package com.affiliate.platform.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 金融复式记账账本定时自动对账巡检调度器 (Immutable Ledger Reconciliation Scheduler)
 * <p>
 * 遵循商业级演进路线图（阶段 1 - 资金账本平衡保障）：
 * 1. 周期性（默认每日凌晨 2:00 或按需触发）对所有租户/账户执行全量复式分录 vs 物化余额核对；
 * 2. 差错审计熔断：一旦检测到任意账户 balance != sum(credit) - sum(debit)，立即触发 P1 财务告警；
 * 3. 统计不平衡账户数，暴露度量指标与巡检报表供运维/财务审计追溯。
 */
@Component
public class LedgerReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationScheduler.class);

    private final WalletService walletService;
    private final com.affiliate.platform.metrics.SloMetricsCollector metricsCollector;
    private final AtomicInteger discrepancyCountTotal = new AtomicInteger(0);
    private final AtomicReference<ReconciliationScanReport> lastScanReport = new AtomicReference<>(null);

    public record ReconciliationScanReport(
            String scanId,
            String tenantId,
            Instant scannedAt,
            int totalScanned,
            int balancedCount,
            int discrepantCount,
            List<WalletService.ReconciliationResult> discrepancies
    ) {}

    public LedgerReconciliationScheduler(WalletService walletService) {
        this(walletService, null);
    }

    @Autowired
    public LedgerReconciliationScheduler(
            WalletService walletService,
            @Autowired(required = false) com.affiliate.platform.metrics.SloMetricsCollector metricsCollector
    ) {
        this.walletService = walletService;
        this.metricsCollector = metricsCollector != null ? metricsCollector : new com.affiliate.platform.metrics.SloMetricsCollector();
    }

    /**
     * 每日定时自动巡检 (生产环境默认凌晨 02:00 启动)
     */
    @Scheduled(cron = "${affiliate.billing.reconcile.cron:0 0 2 * * ?}")
    public void scheduledDailyReconciliation() {
        log.info("[LedgerReconciliationScheduler] 启动例行账本借贷平衡自动巡检...");
        runReconciliationScan(null);
    }

    /**
     * 手动触发或针对特定租户执行全量对账巡检
     *
     * @param tenantId 租户标识 (为 null 或空时巡检全量租户)
     * @return 详细对账巡检结果报告
     */
    public ReconciliationScanReport runReconciliationScan(String tenantId) {
        String scanId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        if (walletService == null) {
            log.warn("[LedgerReconciliationScheduler] WalletService 未注入，跳过本次对账巡检");
            ReconciliationScanReport empty = new ReconciliationScanReport(scanId, tenantId, now, 0, 0, 0, List.of());
            lastScanReport.set(empty);
            return empty;
        }

        List<WalletService.ReconciliationResult> results = walletService.reconcileAll(tenantId);
        int total = results.size();
        int balanced = 0;
        List<WalletService.ReconciliationResult> discrepancies = new ArrayList<>();

        for (WalletService.ReconciliationResult r : results) {
            if (r.balanced()) {
                balanced++;
            } else {
                discrepancies.add(r);
            }
        }

        int discrepantCount = discrepancies.size();
        if (discrepantCount > 0) {
            discrepancyCountTotal.addAndGet(discrepantCount);
            if (metricsCollector != null) {
                for (WalletService.ReconciliationResult disc : discrepancies) {
                    metricsCollector.recordReconcileDiscrepancy(
                            disc.tenantId(),
                            disc.discrepancy() != null ? disc.discrepancy().doubleValue() : 0.0
                    );
                }
            }
            log.error("[CRITICAL_FINANCIAL_ALERT] 自动对账巡检发现 {} 个账户借贷不平或物化余额差错！租户={}, 扫描ID={}, 异常明细: {}",
                    discrepantCount, tenantId != null ? tenantId : "ALL", scanId, discrepancies);
        } else {
            log.info("[LedgerReconciliationScheduler] 账本自动巡检完成，全量平衡通过：已核对账户数={}, 租户={}, 扫描ID={}",
                    total, tenantId != null ? tenantId : "ALL", scanId);
        }

        ReconciliationScanReport report = new ReconciliationScanReport(
                scanId,
                tenantId != null ? tenantId : "ALL",
                now,
                total,
                balanced,
                discrepantCount,
                Collections.unmodifiableList(discrepancies)
        );
        lastScanReport.set(report);
        return report;
    }

    /**
     * 获取累计检测到的不平衡账户总次数（用于监控告警与健康检查）
     */
    public int getDiscrepancyCountTotal() {
        return discrepancyCountTotal.get();
    }

    /**
     * 获取最近一次对账巡检报告
     */
    public ReconciliationScanReport getLastScanReport() {
        return lastScanReport.get();
    }
}
