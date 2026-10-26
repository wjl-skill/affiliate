package com.affiliate.platform.web;

import com.affiliate.platform.dsp.Campaign;
import com.affiliate.platform.dsp.CampaignService;
import com.affiliate.platform.dsp.AiCampaignAdvisoryService;
import com.affiliate.platform.dsp.DeepBiddingOptimizer;
import com.affiliate.platform.dsp.TrafficContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 需求方广告活动管理 REST 控制器 (DSP Campaign REST Controller)
 * <p>
 * 提供广告活动的新建、查看、启停控制以及定向匹配预览接口。
 * 全面集成 Spring Security 方法级细粒度权限管控 (@PreAuthorize)。
 */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    /**
     * 获取全量广告活动列表
     * GET /api/v1/campaigns
     */
    @GetMapping
    @PreAuthorize("@perm.has('campaign:read')")
    public List<Campaign> list() {
        return service.list();
    }

    /**
     * 创建新广告活动
     * POST /api/v1/campaigns
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has('campaign:write')")
    public Campaign create(@Valid @RequestBody Campaign input) {
        return service.create(input);
    }

    /**
     * 根据活动 ID 查询详情
     * GET /api/v1/campaigns/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('campaign:read')")
    public Campaign get(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * 启用或暂停广告活动
     * POST /api/v1/campaigns/{id}/active
     */
    @PostMapping("/{id}/active")
    @PreAuthorize("@perm.has('campaign:write')")
    public Campaign active(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    /**
     * 活动定向匹配度实时检测接口
     * GET /api/v1/campaigns/match?domain=example.com&deviceType=1&date=2026-09-02
     */
    @GetMapping("/match")
    @PreAuthorize("@perm.has('campaign:read')")
    public List<Campaign> match(
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int deviceType,
            @RequestParam(required = false) LocalDate date
    ) {
        return service.match(domain, deviceType, date == null ? LocalDate.now() : date);
    }

    /**
     * 深度学习 pCTR/pCVR 驱动的最优出价测算接口
     * POST /api/v1/campaigns/{id}/optimal-bid
     */
    @PostMapping("/{id}/optimal-bid")
    @PreAuthorize("@perm.has('campaign:read')")
    public DeepBiddingOptimizer.DeepBiddingDecision optimalBid(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "1.00") BigDecimal floorPrice,
            @RequestBody(required = false) TrafficContext context
    ) {
        TrafficContext ctx = context != null ? context : TrafficContext.of("example.com", 1, LocalDate.now());
        return service.evaluateOptimalBid(id, ctx, floorPrice);
    }

    /**
     * 获取 AI 智能出价与投产比优化建议
     */
    @GetMapping("/{id}/ai-advisory")
    @PreAuthorize("@perm.has('campaign:read')")
    public AiCampaignAdvisoryService.CampaignAdvisoryResult getAiAdvisory(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "1.00") BigDecimal floorPrice
    ) {
        TrafficContext ctx = TrafficContext.of("premium-slot.com", 1, LocalDate.now());
        return service.getAiAdvisory(id, ctx, floorPrice);
    }

    public record ActiveRequest(@NotNull Boolean active) {}
}
