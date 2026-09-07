package com.affiliate.platform.web;

import com.affiliate.platform.billing.BillingService;
import com.affiliate.platform.billing.CurrencyFxService;
import com.affiliate.platform.billing.MassPayoutBatchService;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务结算与账本分录 REST 控制器 (Billing REST Controller)
 * <p>
 * 提供账本流水明细查询以及严格幂等记账分录写入接口。
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    // 计费核心服务
    private final BillingService service;
    private final CurrencyFxService fxService;
    private final MassPayoutBatchService payoutService;

    public BillingController(BillingService service) {
        this(service, new CurrencyFxService(), new MassPayoutBatchService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BillingController(
            BillingService service,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CurrencyFxService fxService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) MassPayoutBatchService payoutService
    ) {
        this.service = service;
        this.fxService = fxService != null ? fxService : new CurrencyFxService();
        this.payoutService = payoutService != null ? payoutService : new MassPayoutBatchService(this.fxService);
    }

    /**
     * 查询支持的实时外汇汇率表
     * GET /api/v1/billing/fx/rates
     */
    @GetMapping("/fx/rates")
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
    public java.util.Map<String, Object> updateFxRate(@RequestParam String currency, @RequestParam BigDecimal rate) {
        fxService.updateRate(currency, rate);
        return java.util.Map.of("currency", currency.toUpperCase(), "rate", rate, "updated", true);
    }

    /**
     * 生成出海批量放款出账清单批次
     * POST /api/v1/billing/payouts/batch
     */
    @PostMapping("/payouts/batch")
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
    public java.util.Map<String, Object> disburseBatch(@PathVariable String batchId) {
        boolean success = payoutService.disburseBatch(batchId);
        return java.util.Map.of("batchId", batchId, "success", success, "status", success ? "DISBURSED" : "FAILED");
    }

    /**
     * 查询指定放款批次主信息
     * GET /api/v1/billing/payouts/batch/{batchId}
     */
    @GetMapping("/payouts/batch/{batchId}")
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
    public List<com.affiliate.platform.entity.PayoutItemEntity> getBatchItems(@PathVariable String batchId) {
        return payoutService.listBatchItems(batchId);
    }

    /**
     * 导出 Tipalti 兼容的标准放款 CSV
     * POST /api/v1/billing/payouts/batch/export/tipalti
     */
    @PostMapping(value = "/payouts/batch/export/tipalti", produces = "text/csv")
    public String exportTipalti(@RequestBody MassPayoutBatchService.PayoutBatchManifest manifest) {
        return payoutService.exportTipaltiCsv(manifest);
    }

    /**
     * 导出 Payoneer 批量打款 CSV
     * POST /api/v1/billing/payouts/batch/export/payoneer
     */
    @PostMapping(value = "/payouts/batch/export/payoneer", produces = "text/csv")
    public String exportPayoneer(@RequestBody MassPayoutBatchService.PayoutBatchManifest manifest) {
        return payoutService.exportPayoneerCsv(manifest);
    }

    /**
     * 查询当前租户的所有财务流水明细
     * GET /api/v1/billing/entries
     */
    @GetMapping("/entries")
    public List<BillingService.BillingEntry> list() {
        return service.list(TenantContext.required());
    }

    /**
     * 写入一笔财务计费分录（具备幂等键防护）
     * POST /api/v1/billing/entries
     */
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
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
