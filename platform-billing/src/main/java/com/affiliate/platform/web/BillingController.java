package com.affiliate.platform.web;

import com.affiliate.platform.billing.BillingService;
import com.affiliate.platform.billing.CurrencyFxService;
import com.affiliate.platform.billing.MassPayoutBatchService;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务结算与账本分录 REST 控制器 (Billing REST Controller)
 * <p>
 * 提供账本流水明细查询以及严格幂等记账分录写入接口。
 * 全面集成 Spring Security 方法级细粒度权限管控 (@PreAuthorize)。
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    // 计费核心服务
    private final BillingService service;
    private final CurrencyFxService fxService;
    private final MassPayoutBatchService payoutService;
    private final com.affiliate.platform.billing.WalletService walletService;
    private final com.affiliate.platform.billing.LedgerReconciliationScheduler reconciliationScheduler;

    public BillingController(BillingService service) {
        this(service, new CurrencyFxService(), new MassPayoutBatchService(), null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BillingController(
            BillingService service,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CurrencyFxService fxService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) MassPayoutBatchService payoutService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.affiliate.platform.billing.WalletService walletService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.affiliate.platform.billing.LedgerReconciliationScheduler reconciliationScheduler
    ) {
        this.service = service;
        this.fxService = fxService != null ? fxService : new CurrencyFxService();
        this.payoutService = payoutService != null ? payoutService : new MassPayoutBatchService(this.fxService);
        this.walletService = walletService;
        this.reconciliationScheduler = reconciliationScheduler;
    }

    /**
     * 查询支持的实时外汇汇率表
     * GET /api/v1/billing/fx/rates
     */
    @GetMapping("/fx/rates")
    @PreAuthorize("@perm.has('billing:read')")
    public java.util.Map<String, Object> getFxRates() {
        return java.util.Map.of(
                "EUR", fxService.getRate("EUR"),
                "GBP", fxService.getRate("GBP"),
                "JPY", fxService.getRate("JPY"),
                "SGD", fxService.getRate("SGD"),
                "USDT", fxService.getRate("USDT")
        );
    }

    /**
     * 动态更新目标币种汇率并同步两级缓存与数据库
     * POST /api/v1/billing/fx/rates
     */
    @PostMapping("/fx/rates")
    @PreAuthorize("@perm.has('billing:write')")
    public java.util.Map<String, Object> updateFxRate(@RequestParam String currency, @RequestParam BigDecimal rate) {
        fxService.updateRate(currency, rate);
        return java.util.Map.of("currency", currency.toUpperCase(), "rate", rate, "updated", true);
    }

    /**
     * 生成出海批量放款出账清单批次
     * POST /api/v1/billing/payouts/batch
     */
    @PostMapping("/payouts/batch")
    @PreAuthorize("@perm.has('payout:create')")
    public MassPayoutBatchService.PayoutBatchManifest createBatch(
            @RequestBody List<MassPayoutBatchService.PayoutCandidate> candidates,
            @RequestParam(defaultValue = "100.00") BigDecimal minThreshold,
            @RequestParam(defaultValue = "0.10") BigDecimal taxRate
    ) {
        return payoutService.createBatch(candidates, minThreshold, taxRate);
    }

    /**
     * 放款批次确认下发打款 (State Machine: DRAFT -> DISBURSED)
     * POST /api/v1/billing/payouts/batch/{batchId}/disburse
     */
    @PostMapping("/payouts/batch/{batchId}/disburse")
    @PreAuthorize("@perm.has('payout:disburse')")
    public java.util.Map<String, Object> disburseBatch(@PathVariable String batchId) {
        boolean success = payoutService.disburseBatch(batchId);
        return java.util.Map.of("batchId", batchId, "success", success, "status", success ? "DISBURSED" : "FAILED");
    }

    /**
     * 查询最近的放款批次列表
     * GET /api/v1/billing/payouts/batches
     */
    @GetMapping("/payouts/batches")
    @PreAuthorize("@perm.has('payout:read')")
    public List<com.affiliate.platform.entity.PayoutBatchEntity> listBatches() {
        return payoutService.listBatches();
    }

    /**
     * 查询指定放款批次主信息
     * GET /api/v1/billing/payouts/batch/{batchId}
     */
    @GetMapping("/payouts/batch/{batchId}")
    @PreAuthorize("@perm.has('payout:read')")
    public org.springframework.http.ResponseEntity<com.affiliate.platform.entity.PayoutBatchEntity> getBatch(@PathVariable String batchId) {
        return payoutService.findBatch(batchId)
                .map(org.springframework.http.ResponseEntity::ok)
                .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }

    /**
     * 查询指定放款批次的明细项列表
     * GET /api/v1/billing/payouts/batch/{batchId}/items
     */
    @GetMapping("/payouts/batch/{batchId}/items")
    @PreAuthorize("@perm.has('payout:read')")
    public List<com.affiliate.platform.entity.PayoutItemEntity> getBatchItems(@PathVariable String batchId) {
        return payoutService.listBatchItems(batchId);
    }

    /**
     * 导出 Tipalti 兼容的标准放款 CSV
     * POST /api/v1/billing/payouts/batch/export/tipalti
     */
    @PostMapping(value = "/payouts/batch/export/tipalti", produces = "text/csv")
    @PreAuthorize("@perm.has('payout:export')")
    public String exportTipalti(@RequestBody MassPayoutBatchService.PayoutBatchManifest manifest) {
        return payoutService.exportTipaltiCsv(manifest);
    }

    /**
     * 导出 Payoneer 批量打款 CSV
     * POST /api/v1/billing/payouts/batch/export/payoneer
     */
    @PostMapping(value = "/payouts/batch/export/payoneer", produces = "text/csv")
    @PreAuthorize("@perm.has('payout:export')")
    public String exportPayoneer(@RequestBody MassPayoutBatchService.PayoutBatchManifest manifest) {
        return payoutService.exportPayoneerCsv(manifest);
    }

    /**
     * 导出 PayPal Payouts 批量打款 JSON
     * POST /api/v1/billing/payouts/batch/export/paypal
     */
    @PostMapping(value = "/payouts/batch/export/paypal", produces = "application/json")
    @PreAuthorize("@perm.has('payout:export')")
    public String exportPayPal(@RequestBody MassPayoutBatchService.PayoutBatchManifest manifest) {
        return payoutService.exportPayPalPayoutsJson(manifest);
    }

    /**
     * 导出 USDT / Web3 加密货币批量转账 JSON
     * POST /api/v1/billing/payouts/batch/export/crypto
     */
    @PostMapping(value = "/payouts/batch/export/crypto", produces = "application/json")
    @PreAuthorize("@perm.has('payout:export')")
    public String exportCrypto(@RequestBody MassPayoutBatchService.PayoutBatchManifest manifest) {
        return payoutService.exportCryptoBatchJson(manifest);
    }

    /**
     * 查询当前租户的所有财务流水明细
     * GET /api/v1/billing/entries
     */
    @GetMapping("/entries")
    @PreAuthorize("@perm.has('billing:read')")
    public List<BillingService.BillingEntry> list() {
        return service.list(TenantContext.required());
    }

    /**
     * 写入一笔财务计费分录（具备幂等键防护）
     * POST /api/v1/billing/entries
     */
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has('billing:write')")
    public BillingService.BillingEntry record(@Valid @RequestBody Request request) {
        return service.record(new BillingService.BillingEntry(
                null,
                TenantContext.required(),
                request.accountId(),
                request.auctionId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.idempotencyKey(),
                null
        ));
    }

    /**
     * 冲正已提交的交易记录（需双人复核四眼审批与 ledger:reverse 权限）
     * POST /api/v1/billing/transactions/{id}/reverse
     */
    @PostMapping("/transactions/{id}/reverse")
    @PreAuthorize("@perm.has('ledger:reverse')")
    public com.affiliate.platform.billing.WalletService.ReversalResult reverseTransaction(
            @PathVariable("id") String txnId,
            @Valid @RequestBody ReversalRequest request
    ) {
        if (walletService == null) {
            throw new UnsupportedOperationException("Wallet service not available");
        }
        return walletService.reverseTransaction(
                TenantContext.required(),
                txnId,
                request.reason(),
                request.makerId(),
                request.checkerId(),
                request.idempotencyKey()
        );
    }

    /**
     * 账户日终对账：核对物化余额与全部不可变会计分录的净差值
     * GET /api/v1/billing/reconcile
     */
    @GetMapping("/reconcile")
    @PreAuthorize("@perm.has('billing:admin')")
    public com.affiliate.platform.billing.WalletService.ReconciliationResult reconcile(@RequestParam String accountId) {
        if (walletService == null) {
            throw new UnsupportedOperationException("Wallet service not available");
        }
        return walletService.reconcile(TenantContext.required(), accountId);
    }

    /**
     * 批量自动对账巡检：核对指定租户（或全部账户）借贷分录与物化余额平衡状态
     * POST /api/v1/billing/reconcile/scan
     */
    @PostMapping("/reconcile/scan")
    @PreAuthorize("@perm.has('billing:admin')")
    public com.affiliate.platform.billing.LedgerReconciliationScheduler.ReconciliationScanReport scanReconciliation(
            @RequestParam(required = false) String tenantId
    ) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.get();
        if (reconciliationScheduler != null) {
            return reconciliationScheduler.runReconciliationScan(effectiveTenant);
        }
        if (walletService != null) {
            return new com.affiliate.platform.billing.LedgerReconciliationScheduler(walletService)
                    .runReconciliationScan(effectiveTenant);
        }
        throw new UnsupportedOperationException("Reconciliation service not available");
    }

    public record ReversalRequest(
            @NotBlank String reason,
            @NotBlank String makerId,
            @NotBlank String checkerId,
            String idempotencyKey
    ) {}

    /**
     * 计费入账请求体 DTO
     *
     * @param accountId      关联账户 ID
     * @param auctionId      拍卖成交 ID
     * @param type           交易类型
     * @param amount         金额
     * @param currency       货币
     * @param idempotencyKey 幂等键
     */
    public record Request(
            @NotBlank String accountId,
            String auctionId,
            @NotNull BillingService.EntryType type,
            @NotNull BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String idempotencyKey
    ) {}
}
