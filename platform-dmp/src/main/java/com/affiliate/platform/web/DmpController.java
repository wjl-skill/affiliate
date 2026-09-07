package com.affiliate.platform.web;

import com.affiliate.platform.dmp.AudienceSegment;
import com.affiliate.platform.dmp.DmpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 数据管理平台 REST 控制器 (DMP REST Controller)
 * <p>
 * 提供匿名受众分群的创建、启用、成员批量导入以及在线人群归属判定。
 */
@RestController
@RequestMapping("/api/v1/dmp/segments")
public class DmpController {

    // DMP 业务服务
    private final DmpService service;

    public DmpController(DmpService service) {
        this.service = service;
    }

    /**
     * 查询当前租户的所有受众分群列表
     * GET /api/v1/dmp/segments
     */
    @GetMapping
    public List<AudienceSegment> list() {
        return service.list();
    }

    /**
     * 创建新受众分群
     * POST /api/v1/dmp/segments
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AudienceSegment create(@Valid @RequestBody AudienceSegment input) {
        return service.create(input);
    }

    /**
     * 根据分群 ID 获取分群元数据详情
     * GET /api/v1/dmp/segments/{id}
     */
    @GetMapping("/{id}")
    public AudienceSegment get(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * 启用或停用受众分群
     * POST /api/v1/dmp/segments/{id}/active
     */
    @PostMapping("/{id}/active")
    public AudienceSegment active(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return service.activate(id, request.active());
    }

    /**
     * 向分群批量导入匿名用户标识 ID
     * POST /api/v1/dmp/segments/{id}/members
     */
    @PostMapping("/{id}/members")
    public AudienceSegment members(@PathVariable String id, @Valid @RequestBody MembersRequest request) {
        return service.addMembers(id, request.anonymousIds());
    }

    /**
     * 校验特定匿名 ID 是否命中有效分群
     * GET /api/v1/dmp/segments/{id}/contains?anonymousId=cookie123
     */
    @GetMapping("/{id}/contains")
    public boolean contains(@PathVariable String id, @RequestParam @NotBlank String anonymousId) {
        return service.contains(id, anonymousId);
    }

    /**
     * 分群启停请求体 DTO
     */
    public record ActiveRequest(@NotNull Boolean active) {}

    /**
     * 成员批量导入请求体 DTO
     */
    public record MembersRequest(@NotNull Set<@NotBlank String> anonymousIds) {}
}
