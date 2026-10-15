package com.affiliate.platform.budget.web;

import com.affiliate.platform.budget.BudgetAlertService;
import com.affiliate.platform.budget.BudgetService;
import com.affiliate.platform.entity.CampaignBudgetEntity;
import com.affiliate.platform.mapper.CampaignBudgetMapper;
import com.affiliate.platform.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 广告活动预算管理与实时预占控制 REST API (Budget Management REST Controller)
 */
@RestController
@RequestMapping("/api/v1/budget")
public class BudgetController {

    private final BudgetService budgetService;
    private final CampaignBudgetMapper campaignBudgetMapper;
    private final BudgetAlertService alertService;

    public BudgetController(
            BudgetService budgetService,
            @Autowired(required = false) CampaignBudgetMapper campaignBudgetMapper,
            @Autowired(required = false) BudgetAlertService alertService
    ) {
        this.budgetService = budgetService;
        this.campaignBudgetMapper = campaignBudgetMapper;
        this.alertService = alertService;
    }

    /**
     * 查询广告活动预算状态与剩余额度
     * GET /api/v1/budget/campaigns/{campaignId}
     */
    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<?> getCampaignBudget(@PathVariable String campaignId) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        if (campaignBudgetMapper != null) {
            CampaignBudgetEntity entity = campaignBudgetMapper.selectOne(
                    new LambdaQueryWrapper<CampaignBudgetEntity>()
                            .eq(CampaignBudgetEntity::getTenantId, tenantId)
                            .eq(CampaignBudgetEntity::getCampaignId, campaignId)
            );
            if (entity != null) {
                return ResponseEntity.ok(entity);
            }
        }
        return ResponseEntity.ok(Map.of(
                "campaignId", campaignId,
                "tenantId", tenantId,
                "balance", BigDecimal.ZERO,
                "status", "NOT_CONFIGURED"
        ));
    }

    /**
     * 配置/注资广告活动预算
     * POST /api/v1/budget/campaigns/{campaignId}
     */
    @PostMapping("/campaigns/{campaignId}")
    public ResponseEntity<?> setCampaignBudget(
            @PathVariable String campaignId,
            @Valid @RequestBody SetBudgetRequest request
    ) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        budgetService.setBudget(tenantId, campaignId, request.amount());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "campaignId", campaignId,
                "amount", request.amount()
        ));
    }

    /**
     * 竞价前原子预占扣除
     * POST /api/v1/budget/reserve
     */
    @PostMapping("/reserve")
    public ResponseEntity<?> reserve(@Valid @RequestBody ReserveRequest request) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        BudgetService.Reservation reservation = budgetService.reserve(
                tenantId,
                request.campaignId(),
                request.userId(),
                request.amount()
        );
        return ResponseEntity.ok(reservation);
    }

    /**
     * 胜出通知确认扣款 (转正预占资金)
     * POST /api/v1/budget/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@Valid @RequestBody ConfirmOrReleaseRequest request) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        BudgetService.Reservation reservation = new BudgetService.Reservation(
                request.reservationId(),
                tenantId,
                request.campaignId(),
                request.userId(),
                request.amount()
        );
        budgetService.confirm(reservation);
        return ResponseEntity.ok(Map.of("success", true, "action", "CONFIRMED"));
    }

    /**
     * 未胜出原路释放资金
     * POST /api/v1/budget/release
     */
    @PostMapping("/release")
    public ResponseEntity<?> release(@Valid @RequestBody ConfirmOrReleaseRequest request) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        BudgetService.Reservation reservation = new BudgetService.Reservation(
                request.reservationId(),
                tenantId,
                request.campaignId(),
                request.userId(),
                request.amount()
        );
        budgetService.release(reservation);
        return ResponseEntity.ok(Map.of("success", true, "action", "RELEASED"));
    }

    /**
     * 追加注资/返还切片
     * POST /api/v1/budget/campaigns/{campaignId}/credit
     */
    @PostMapping("/campaigns/{campaignId}/credit")
    public ResponseEntity<?> creditBudget(
            @PathVariable String campaignId,
            @Valid @RequestBody CreditBudgetRequest request
    ) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        budgetService.creditBudget(tenantId, campaignId, request.amount());
        return ResponseEntity.ok(Map.of("success", true, "action", "CREDITED"));
    }

    /**
     * 检查并触发预算消耗告警水位
     * GET /api/v1/budget/campaigns/{campaignId}/alert
     */
    @GetMapping("/campaigns/{campaignId}/alert")
    public ResponseEntity<?> checkAlert(
            @PathVariable String campaignId,
            @RequestParam BigDecimal currentSpend,
            @RequestParam BigDecimal dailyBudget
    ) {
        String tenantId = TenantContext.get() != null ? TenantContext.get() : "public";
        int level = alertService != null
                ? alertService.checkAndAlert(tenantId, campaignId, currentSpend, dailyBudget)
                : 0;
        return ResponseEntity.ok(Map.of("campaignId", campaignId, "alertLevel", level));
    }

    public record SetBudgetRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            BigDecimal dailyBudget
    ) {}

    public record ReserveRequest(
            @NotBlank String campaignId,
            @NotBlank String userId,
            @NotNull @DecimalMin("0.0001") BigDecimal amount
    ) {}

    public record ConfirmOrReleaseRequest(
            @NotBlank String reservationId,
            @NotBlank String campaignId,
            String userId,
            @NotNull BigDecimal amount
    ) {}

    public record CreditBudgetRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}
}
