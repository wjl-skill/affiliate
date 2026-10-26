package com.affiliate.platform.web;

import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.service.AdSlotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 媒体广告位管理 REST 控制器 (SSP Ad Slot REST Controller)
 * <p>
 * 面向媒体发布商（Publisher），提供广告位的注册开通、按 ID 详情查看、全量列表查看与在线启停管理。
 * 全面集成 Spring Security 方法级细粒度权限管控 (@PreAuthorize)。
 */
@RestController
@RequestMapping("/api/v1/ad-slots")
public class AdSlotController {

    // 广告位业务服务
    private final AdSlotService service;

    public AdSlotController(AdSlotService service) {
        this.service = service;
    }

    /**
     * 获取全量媒体广告位配置列表
     * GET /api/v1/ad-slots
     */
    @GetMapping
    @PreAuthorize("@perm.has('deal:read')")
    public List<AdSlot> list() {
        return service.list();
    }

    /**
     * 注册开通新广告位
     * POST /api/v1/ad-slots
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has('deal:write')")
    public AdSlot create(@Valid @RequestBody AdSlot input) {
        return service.create(input);
    }

    /**
     * 根据 ID 检索广告位配置详情
     * GET /api/v1/ad-slots/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('deal:read')")
    public AdSlot get(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * 启用或下线指定广告位
     * POST /api/v1/ad-slots/{id}/active
     */
    @PostMapping("/{id}/active")
    @PreAuthorize("@perm.has('deal:write')")
    public AdSlot active(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    /**
     * 广告位启停请求载荷 DTO
     */
    public record ActiveRequest(@NotNull Boolean active) {}
}
