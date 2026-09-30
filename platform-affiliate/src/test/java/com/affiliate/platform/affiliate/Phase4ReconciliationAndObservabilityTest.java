package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.PaymentTransactionEntity;
import com.affiliate.platform.affiliate.metrics.AffiliateMetrics;
import com.affiliate.platform.affiliate.repository.PaymentTransactionRepository;
import com.affiliate.platform.affiliate.service.AffiliateSettlementService;
import com.affiliate.platform.affiliate.service.FinancialReconciliationService;
import com.affiliate.platform.affiliate.service.S2sPostbackService;
import com.affiliate.platform.trace.TraceContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 四阶段深化测试：链路追踪上下文、核心全链路指标与金融级自动化对账引擎
 */
class Phase4ReconciliationAndObservabilityTest {

    private AffiliateSettlementService settlementService;
    private S2sPostbackService postbackService;
    private PaymentTransactionRepository transactionRepository;
    private FinancialReconciliationService reconciliationService;
    private SimpleMeterRegistry meterRegistry;
    private AffiliateMetrics metrics;

    @BeforeEach
    void setUp() {
        settlementService = Mockito.mock(AffiliateSettlementService.class);
        postbackService = Mockito.mock(S2sPostbackService.class);
        transactionRepository = Mockito.mock(PaymentTransactionRepository.class);
        reconciliationService = new FinancialReconciliationService(settlementService, postbackService, transactionRepository);

        meterRegistry = new SimpleMeterRegistry();
        metrics = new AffiliateMetrics(meterRegistry);
    }

    @Test
    @DisplayName("测试 TraceContext 全链路追踪与 SLF4J MDC 上下文同步及自动清理")
    void testTraceContextAndMdcLifecycle() {
        TraceContext.clear();
        assertNull(TraceContext.get());
        assertNull(MDC.get(TraceContext.MDC_TRACE_KEY));

        String testTrace = "trace_prod_8888";
        TraceContext.set(testTrace);
        assertEquals(testTrace, TraceContext.get());
        assertEquals(testTrace, MDC.get(TraceContext.MDC_TRACE_KEY));

        TraceContext.clear();
        assertNull(TraceContext.get());
        assertNull(MDC.get(TraceContext.MDC_TRACE_KEY));

        // 测试自动生成
        String autoTrace = TraceContext.getOrCreate();
        assertNotNull(autoTrace);
        assertEquals(autoTrace, TraceContext.get());
        TraceContext.clear();
    }

    @Test
    @DisplayName("测试 AffiliateMetrics 生产级核心指标度量与计数")
    void testAffiliateMetricsCounting() {
        // 1. 点击成功与丢弃计数
        metrics.recordClickSuccess(15_000_000L); // 15ms
        metrics.recordClickSuccess(10_000_000L); // 10ms
        metrics.recordClickDropped();

        assertEquals(2.0, meterRegistry.get("affiliate.clicks.total").tag("status", "success").counter().count());
        assertEquals(1.0, meterRegistry.get("affiliate.clicks.total").tag("status", "dropped").counter().count());

        // 2. 转化状态与风控计数
        metrics.recordConversionApproved();
        metrics.recordConversionApproved();
        metrics.recordConversionFraud();
        metrics.recordConversionRejected();
        metrics.recordPostbackLockContention();

        assertEquals(2.0, meterRegistry.get("affiliate.conversions.total").tag("status", "approved").counter().count());
        assertEquals(1.0, meterRegistry.get("affiliate.conversions.total").tag("status", "fraud_suspected").counter().count());
        assertEquals(1.0, meterRegistry.get("affiliate.conversions.total").tag("status", "rejected").counter().count());
        assertEquals(1.0, meterRegistry.get("affiliate.postback.lock.contention.total").counter().count());

        // 3. TDS 路由与降级 Offer 数量
        metrics.recordTdsRouteRoundRobin();
        metrics.recordTdsRouteHighestEpc();
        metrics.recordTdsRouteFallback();
        metrics.updateDegradedOfferCount(3);

        assertEquals(1.0, meterRegistry.get("affiliate.tds.routes.total").tag("strategy", "round_robin").counter().count());
        assertEquals(1.0, meterRegistry.get("affiliate.tds.routes.total").tag("strategy", "highest_epc").counter().count());
        assertEquals(1.0, meterRegistry.get("affiliate.tds.fallback.total").counter().count());
        assertEquals(3.0, meterRegistry.get("affiliate.tds.offers.degraded").gauge().value());
    }

    @Test
    @DisplayName("测试金融级自动化对账引擎：发票与流水完全平账场景")
    void testReconciliationPerfectMatch() {
        String tenant = "t_prod_1";
        AffiliateSettlementService.AffiliateInvoice invoice = new AffiliateSettlementService.AffiliateInvoice(
                "inv_101", tenant, "aff_1", "CYCLE_2026-09",
                new BigDecimal("1500.00"), 10, AffiliatePartner.PaymentTerm.NET_30,
                AffiliateSettlementService.InvoiceStatus.PAID, Instant.now(), Instant.now()
        );

        PaymentTransactionEntity tx = new PaymentTransactionEntity(
                "tx_001", "aff_1", "inv_101", "pm_1",
                new BigDecimal("1500.00"), "USD", BigDecimal.ZERO, new BigDecimal("1500.00"),
                "USD", BigDecimal.ONE, new BigDecimal("1500.00"), "COMPLETED",
                "ext_paypal_101", null, 0, Instant.now(), Instant.now(), null
        );

        when(settlementService.listInvoices()).thenReturn(List.of(invoice));
        when(postbackService.listConversions()).thenReturn(List.of());
        when(transactionRepository.findAllByInvoiceId("inv_101")).thenReturn(List.of(tx));

        FinancialReconciliationService.AuditReport report = reconciliationService.auditTenant(tenant);

        assertTrue(report.isBalanced());
        assertEquals(0, report.breaks().size());
        assertEquals(new BigDecimal("1500.00"), report.totalInvoiceAmount());
        assertEquals(new BigDecimal("1500.00"), report.totalSettledAmount());
    }

    @Test
    @DisplayName("测试金融级自动化对账引擎：精准捕获超额重复付款、无流水已付与金额偏差差错")
    void testReconciliationDetectsBreaks() {
        String tenant = "t_prod_2";

        // 发票 1: 已标记为 PAID，但外部支付流水不存在 (虚假核销)
        AffiliateSettlementService.AffiliateInvoice invMissingTx = new AffiliateSettlementService.AffiliateInvoice(
                "inv_missing_tx", tenant, "aff_1", "CYCLE_2026-09",
                new BigDecimal("800.00"), 5, AffiliatePartner.PaymentTerm.NET_30,
                AffiliateSettlementService.InvoiceStatus.PAID, Instant.now(), Instant.now()
        );

        // 发票 2: 已标记为 PAID，但网关中存在两笔 COMPLETED 流水 (严重重复付款风险)
        AffiliateSettlementService.AffiliateInvoice invDuplicate = new AffiliateSettlementService.AffiliateInvoice(
                "inv_duplicate", tenant, "aff_2", "CYCLE_2026-09",
                new BigDecimal("500.00"), 3, AffiliatePartner.PaymentTerm.NET_30,
                AffiliateSettlementService.InvoiceStatus.PAID, Instant.now(), Instant.now()
        );

        PaymentTransactionEntity txDup1 = new PaymentTransactionEntity(
                "tx_d1", "aff_2", "inv_duplicate", "pm_1",
                new BigDecimal("500.00"), "USD", BigDecimal.ZERO, new BigDecimal("500.00"),
                "USD", BigDecimal.ONE, new BigDecimal("500.00"), "COMPLETED",
                "ext_pp_1", null, 0, Instant.now(), Instant.now(), null
        );
        PaymentTransactionEntity txDup2 = new PaymentTransactionEntity(
                "tx_d2", "aff_2", "inv_duplicate", "pm_1",
                new BigDecimal("500.00"), "USD", BigDecimal.ZERO, new BigDecimal("500.00"),
                "USD", BigDecimal.ONE, new BigDecimal("500.00"), "COMPLETED",
                "ext_pp_2", null, 0, Instant.now(), Instant.now(), null
        );

        // 发票 3: 金额不匹配 (发票 1000.00，实付 950.00)
        AffiliateSettlementService.AffiliateInvoice invMismatch = new AffiliateSettlementService.AffiliateInvoice(
                "inv_mismatch", tenant, "aff_3", "CYCLE_2026-09",
                new BigDecimal("1000.00"), 8, AffiliatePartner.PaymentTerm.NET_30,
                AffiliateSettlementService.InvoiceStatus.PAID, Instant.now(), Instant.now()
        );
        PaymentTransactionEntity txMismatch = new PaymentTransactionEntity(
                "tx_m1", "aff_3", "inv_mismatch", "pm_1",
                new BigDecimal("950.00"), "USD", BigDecimal.ZERO, new BigDecimal("950.00"),
                "USD", BigDecimal.ONE, new BigDecimal("950.00"), "COMPLETED",
                "ext_pp_3", null, 0, Instant.now(), Instant.now(), null
        );

        when(settlementService.listInvoices()).thenReturn(List.of(invMissingTx, invDuplicate, invMismatch));
        when(postbackService.listConversions()).thenReturn(List.of());
        when(transactionRepository.findAllByInvoiceId("inv_missing_tx")).thenReturn(List.of());
        when(transactionRepository.findAllByInvoiceId("inv_duplicate")).thenReturn(List.of(txDup1, txDup2));
        when(transactionRepository.findAllByInvoiceId("inv_mismatch")).thenReturn(List.of(txMismatch));

        FinancialReconciliationService.AuditReport report = reconciliationService.auditTenant(tenant);

        assertFalse(report.isBalanced());
        assertEquals(3, report.breaks().size());

        assertTrue(report.breaks().stream().anyMatch(b -> b.type() == FinancialReconciliationService.BreakType.PAID_INVOICE_MISSING_PAYMENT));
        assertTrue(report.breaks().stream().anyMatch(b -> b.type() == FinancialReconciliationService.BreakType.DUPLICATE_PAYMENT));
        assertTrue(report.breaks().stream().anyMatch(b -> b.type() == FinancialReconciliationService.BreakType.INVOICE_AMOUNT_MISMATCH));
    }
}
