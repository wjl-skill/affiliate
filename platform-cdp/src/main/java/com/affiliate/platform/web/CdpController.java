package com.affiliate.platform.web;

import com.affiliate.platform.cdp.CustomerProfile;
import com.affiliate.platform.cdp.IdentityMappingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.affiliate.platform.cdp.RealtimeEventTraitEngine;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 客户数据平台一方画像 REST 控制器 (CDP REST Controller)
 * <p>
 * 提供一方客户画像的初始化、跨端标识反查解析、属性标签增量合并、隐私退出及合规擦除接口。
 */
@RestController
@RequestMapping("/api/v1/cdp/profiles")
public class CdpController {

    // 身份打通业务服务
    private final IdentityMappingService service;
    private final RealtimeEventTraitEngine traitEngine;

    @Autowired
    public CdpController(
            IdentityMappingService service,
            @Autowired(required = false) RealtimeEventTraitEngine traitEngine
    ) {
        this.service = service;
        this.traitEngine = traitEngine != null ? traitEngine : new RealtimeEventTraitEngine(service, null);
    }

    /**
     * 查询当前租户名下的所有客户档案列表
     * GET /api/v1/cdp/profiles
     */
    @GetMapping
    public List<CustomerProfile> list() {
        return service.list();
    }

    /**
     * 注册创建新客户画像档案
     * POST /api/v1/cdp/profiles
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerProfile create(@Valid @RequestBody CustomerProfile input) {
        return service.create(input);
    }

    /**
     * 根据任意渠道标识（手机、邮箱、OpenID 等）反查唯一定位客户档案
     * GET /api/v1/cdp/profiles/resolve?identifier=user@example.com
     */
    @GetMapping("/resolve")
    public CustomerProfile resolve(@RequestParam @NotBlank String identifier) {
        return service.resolve(identifier);
    }

    /**
     * 跨触点增量合并标识与标签属性
     * POST /api/v1/cdp/profiles/{primaryId}/merge
     */
    @PostMapping("/{primaryId}/merge")
    public CustomerProfile merge(@PathVariable String primaryId, @Valid @RequestBody MergeRequest request) {
        return service.merge(primaryId, request.identifiers(), request.attributes(), request.traits());
    }

    /**
     * 标记客户隐私选择退出营销定向
     * POST /api/v1/cdp/profiles/{primaryId}/opt-out
     */
    @PostMapping("/{primaryId}/opt-out")
    public CustomerProfile optOut(@PathVariable String primaryId) {
        return service.optOut(primaryId);
    }

    /**
     * 执行 GDPR/CCPA 被遗忘权物理删除
     * DELETE /api/v1/cdp/profiles/{primaryId}
     */
    @DeleteMapping("/{primaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable String primaryId) {
        service.erase(primaryId);
    }

    /**
     * 实时摄入用户全触点行为流
     * POST /api/v1/cdp/profiles/{primaryId}/events
     */
    @PostMapping("/{primaryId}/events")
    public RealtimeEventTraitEngine.UserBehaviorSnapshot ingestEvent(
            @PathVariable String primaryId,
            @Valid @RequestBody EventIngestRequest req
    ) {
        return traitEngine.ingestEvent(
                primaryId,
                req.type() != null ? req.type() : RealtimeEventTraitEngine.EventType.CLICK,
                req.amount(),
                req.category(),
                req.deviceType(),
                req.timestamp() != null ? req.timestamp() : Instant.now()
        );
    }

    /**
     * 获取用户行为与动态特征标签快照
     * GET /api/v1/cdp/profiles/{primaryId}/traits
     */
    @GetMapping("/{primaryId}/traits")
    public RealtimeEventTraitEngine.UserBehaviorSnapshot getTraits(@PathVariable String primaryId) {
        return traitEngine.getSnapshot(primaryId).orElse(null);
    }

    /**
     * 实时行为摄入请求体
     */
    public record EventIngestRequest(
            RealtimeEventTraitEngine.EventType type,
            BigDecimal amount,
            String category,
            String deviceType,
            Instant timestamp
    ) {}

    /**
     * 身份与特征增量合并请求体 DTO
     */
    public record MergeRequest(
            @NotNull Set<@NotBlank String> identifiers,
            Map<String, String> attributes,
            Set<String> traits
    ) {}
}
