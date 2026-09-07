package com.affiliate.platform.web;

import com.affiliate.platform.tenant.Tenant;
import com.affiliate.platform.tenant.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多租户管理 REST 控制器 (Tenant REST Controller)
 * <p>
 * 提供租户的创建、查询与状态启停等 RESTful 接口。
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    // 租户业务服务
    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    /**
     * 查询所有租户列表
     * GET /api/v1/tenants
     */
    @GetMapping
    public List<Tenant> list() {
        return service.list();
    }

    /**
     * 注册创建新租户
     * POST /api/v1/tenants
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant create(@Valid @RequestBody Tenant input) {
        return service.create(input);
    }

    /**
     * 更新租户激活状态
     * POST /api/v1/tenants/{id}/active
     */
    @PostMapping("/{id}/active")
    public Tenant active(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    /**
     * 租户状态变更请求体 DTO
     */
    public record ActiveRequest(@NotNull Boolean active) {}
}
