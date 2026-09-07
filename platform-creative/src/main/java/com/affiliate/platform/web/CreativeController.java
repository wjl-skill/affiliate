package com.affiliate.platform.web;

import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.service.CreativeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 广告素材物料管理 REST 控制器 (Creative REST Controller)
 * <p>
 * 提供广告主/代理商素材的注册上传、全量物料列表查询、单个物料详情获取以及投放上下线控制。
 */
@RestController
@RequestMapping("/api/v1/creatives")
public class CreativeController {

    // 素材业务服务
    private final CreativeService service;

    public CreativeController(CreativeService service) {
        this.service = service;
    }

    /**
     * 获取全量广告素材列表
     * GET /api/v1/creatives
     */
    @GetMapping
    public List<Creative> list() {
        return service.list();
    }

    /**
     * 创建并持久化新广告素材
     * POST /api/v1/creatives
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Creative create(@Valid @RequestBody Creative input) {
        return service.create(input);
    }

    /**
     * 根据素材 ID 获取物料详情
     * GET /api/v1/creatives/{id}
     */
    @GetMapping("/{id}")
    public Creative get(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * 启用或暂停广告素材投放
     * POST /api/v1/creatives/{id}/active
     */
    @PostMapping("/{id}/active")
    public Creative active(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    /**
     * 素材启停状态变更请求体 DTO
     */
    public record ActiveRequest(@NotNull Boolean active) {}
}
