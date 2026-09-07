package com.affiliate.platform.web;

import com.affiliate.platform.dsp.Campaign;
import com.affiliate.platform.dsp.CampaignService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 需求方广告活动管理 REST 控制器 (DSP Campaign REST Controller)
 * <p>
 * 提供广告活动的新建、查看、启停控制以及定向匹配预览接口。
 */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    // 广告活动业务服务
    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    /**
     * 获取全量广告活动列表
     * GET /api/v1/campaigns
     */
    @GetMapping
    public List<Campaign> list() {
        return service.list();
    }

    /**
     * 创建新广告活动
     * POST /api/v1/campaigns
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Campaign create(@Valid @RequestBody Campaign input) {
        return service.create(input);
    }

    /**
     * 根据活动 ID 查询详情
     * GET /api/v1/campaigns/{id}
     */
    @GetMapping("/{id}")
    public Campaign get(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * 启用或暂停广告活动
     * POST /api/v1/campaigns/{id}/active
     */
    @PostMapping("/{id}/active")
    public Campaign active(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    /**
     * 活动定向匹配度实时检测接口
     * GET /api/v1/campaigns/match?domain=example.com&deviceType=1&date=2026-09-02
     */
    @GetMapping("/match")
    public List<Campaign> match(
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int deviceType,
            @RequestParam(required = false) LocalDate date
    ) {
        return service.match(domain, deviceType, date == null ? LocalDate.now() : date);
    }

    /**
     * 活动启停请求载荷 DTO
     */
    public record ActiveRequest(@NotNull Boolean active) {}
}
