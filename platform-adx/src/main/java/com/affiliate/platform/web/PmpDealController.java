package com.affiliate.platform.web;

import com.affiliate.platform.rtb.PmpDealMatcher;
import com.affiliate.platform.rtb.PmpDealMatcher.DealType;
import com.affiliate.platform.rtb.PmpDealMatcher.RegisteredDeal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * ADX 私有交易市场协议 (PMP Deals) REST 控制器
 * <p>
 * 支持 Programmatic Guaranteed (PG), Preferred Deal, Private Auction 白名单规则与出价底价管理。
 * 集成方法级细粒度权限控制 (@PreAuthorize)。
 */
@RestController
@RequestMapping("/api/v1/adx/deals")
public class PmpDealController {

    private final PmpDealMatcher dealMatcher;

    public PmpDealController(PmpDealMatcher dealMatcher) {
        this.dealMatcher = dealMatcher;
    }

    /**
     * 查询所有已注册的 PMP 私有交易合约
     * GET /api/v1/adx/deals
     */
    @GetMapping
    @PreAuthorize("@perm.has('deal:read')")
    public ResponseEntity<Collection<RegisteredDeal>> listDeals() {
        return ResponseEntity.ok(dealMatcher.getAllDeals());
    }

    /**
     * 根据 Deal ID 查询详情
     * GET /api/v1/adx/deals/{dealId}
     */
    @GetMapping("/{dealId}")
    @PreAuthorize("@perm.has('deal:read')")
    public ResponseEntity<?> getDeal(@PathVariable String dealId) {
        return dealMatcher.getDeal(dealId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 注册或更新私有交易合约
     * POST /api/v1/adx/deals
     */
    @PostMapping
    @PreAuthorize("@perm.has('deal:write')")
    public ResponseEntity<RegisteredDeal> createOrUpdateDeal(@Valid @RequestBody DealDto request) {
        RegisteredDeal deal = new RegisteredDeal(
                request.dealId(),
                request.advertiserId(),
                request.dealType(),
                request.floorPrice(),
                request.allowedSeats() != null ? request.allowedSeats() : Set.of(),
                request.active() != null ? request.active() : true
        );
        dealMatcher.registerDeal(deal);
        return ResponseEntity.ok(deal);
    }

    /**
     * 删除私有交易合约
     * DELETE /api/v1/adx/deals/{dealId}
     */
    @DeleteMapping("/{dealId}")
    @PreAuthorize("@perm.has('deal:write')")
    public ResponseEntity<Map<String, Object>> deleteDeal(@PathVariable String dealId) {
        dealMatcher.removeDeal(dealId);
        return ResponseEntity.ok(Map.of("success", true, "dealId", dealId, "message", "Deal removed successfully"));
    }

    /**
     * 试算买方席位出价与私有交易规则撮合
     * POST /api/v1/adx/deals/match
     */
    @PostMapping("/match")
    @PreAuthorize("@perm.has('deal:write')")
    public ResponseEntity<PmpDealMatcher.DealMatchResult> matchDeal(@Valid @RequestBody MatchTestRequest request) {
        PmpDealMatcher.DealMatchResult result = dealMatcher.matchDeal(
                request.dealId(),
                request.buyerSeat(),
                request.bidPrice()
        );
        return ResponseEntity.ok(result);
    }

    public record DealDto(
            @NotBlank String dealId,
            @NotBlank String advertiserId,
            @NotNull DealType dealType,
            @NotNull @DecimalMin("0.0001") BigDecimal floorPrice,
            Set<String> allowedSeats,
            Boolean active
    ) {}

    public record MatchTestRequest(
            @NotBlank String dealId,
            @NotBlank String buyerSeat,
            @NotNull BigDecimal bidPrice
    ) {}
}
