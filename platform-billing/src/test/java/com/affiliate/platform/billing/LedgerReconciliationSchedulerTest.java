package com.affiliate.platform.billing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("金融账本自动巡检对账调度器测试 (LedgerReconciliationSchedulerTest)")
class LedgerReconciliationSchedulerTest {

    private WalletService walletService;
    private LedgerReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        walletService = new WalletService();
        scheduler = new LedgerReconciliationScheduler(walletService);
    }

    @AfterEach
    void tearDown() {
        com.affiliate.platform.tenant.TenantContext.clear();
    }

    @Test
    @DisplayName("全量账户借贷平衡时，自动巡检报告 100% 平衡且无差异项")
    void testScanAllBalancedAccounts() {
        String t1 = "tenant-fin-01";
        walletService.getOrCreate(t1, "acc-001", BigDecimal.ZERO);
        walletService.getOrCreate(t1, "acc-002", BigDecimal.ZERO);

        walletService.recharge("acc-001", new BigDecimal("100.00"), "recharge-1");
        walletService.recharge("acc-002", new BigDecimal("250.00"), "recharge-2");
        walletService.capture("acc-001", new BigDecimal("30.00"), "auc-1", "capture-1");

        LedgerReconciliationScheduler.ReconciliationScanReport report = scheduler.runReconciliationScan(t1);

        assertNotNull(report);
        assertEquals(2, report.totalScanned());
        assertEquals(2, report.balancedCount());
        assertEquals(0, report.discrepantCount());
        assertTrue(report.discrepancies().isEmpty());
        assertEquals(0, scheduler.getDiscrepancyCountTotal());
        assertEquals(report, scheduler.getLastScanReport());
    }

    @Test
    @DisplayName("当出现单边账或物化余额差错时，自动巡检精准捕捉差额并触发差错计数递增")
    void testScanDetectsDiscrepancy() {
        String t2 = "tenant-fin-02";
        walletService.getOrCreate(t2, "acc-normal", BigDecimal.ZERO);
        walletService.getOrCreate(t2, "acc-tampered", BigDecimal.ZERO);

        // 正常账户充值
        walletService.recharge("acc-normal", new BigDecimal("500.00"), "recharge-normal");

        // 异常账户充值 200.00
        walletService.recharge("acc-tampered", new BigDecimal("200.00"), "recharge-tampered");

        // 人为注入物化余额篡改：账本净额 200.00，物化余额改为 999.00，差额 799.00
        WalletAccount tampered = new WalletAccount(
                "acc-tampered",
                t2,
                new BigDecimal("999.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD",
                Instant.now()
        );
        try {
            var field = WalletService.class.getDeclaredField("accounts");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.concurrent.ConcurrentMap<String, WalletAccount>) field.get(walletService);
            map.put("acc-tampered", tampered);
        } catch (Exception e) {
            fail("反射注入测试数据失败: " + e.getMessage());
        }

        LedgerReconciliationScheduler.ReconciliationScanReport report = scheduler.runReconciliationScan(t2);

        assertNotNull(report);
        assertEquals(2, report.totalScanned());
        assertEquals(1, report.balancedCount());
        assertEquals(1, report.discrepantCount());
        assertEquals(1, scheduler.getDiscrepancyCountTotal());

        WalletService.ReconciliationResult discrepancy = report.discrepancies().get(0);
        assertEquals("acc-tampered", discrepancy.accountId());
        assertFalse(discrepancy.balanced());
        assertEquals(new BigDecimal("999.00"), discrepancy.cashBalance());
        assertEquals(new BigDecimal("200.00"), discrepancy.ledgerNetBalance());
        assertEquals(new BigDecimal("799.00"), discrepancy.discrepancy());
    }

    @Test
    @DisplayName("按租户过滤巡检时，仅核算属于该租户的目标账户")
    void testTenantFilteredScan() {
        walletService.getOrCreate("tenant-A", "acc-A", BigDecimal.ZERO);
        walletService.getOrCreate("tenant-B", "acc-B", BigDecimal.ZERO);

        walletService.recharge("acc-A", new BigDecimal("50.00"), "recharge-A");
        walletService.recharge("acc-B", new BigDecimal("80.00"), "recharge-B");

        LedgerReconciliationScheduler.ReconciliationScanReport reportA = scheduler.runReconciliationScan("tenant-A");
        assertEquals(1, reportA.totalScanned());
        assertEquals("tenant-A", reportA.tenantId());
        assertEquals(1, reportA.balancedCount());

        LedgerReconciliationScheduler.ReconciliationScanReport reportAll = scheduler.runReconciliationScan(null);
        assertEquals(2, reportAll.totalScanned());
        assertEquals("ALL", reportAll.tenantId());
        assertEquals(2, reportAll.balancedCount());
        assertEquals(0, reportAll.discrepantCount());
    }
}
