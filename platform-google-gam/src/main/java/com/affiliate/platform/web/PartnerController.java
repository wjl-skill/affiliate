package com.affiliate.platform.web;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.integration.AdPlatformConnector;
import com.affiliate.platform.service.PartnerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 外部合作方连接集成 REST 控制器 (Partner REST Controller)
 * <p>
 * 提供外部平台连接管理、在线状态切换以及手动触发异步物料/报表数据同步等接口。
 */
@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {

    // 合作方基础服务
    private final PartnerService service;

    // 已注入的所有三方连接器映射表：Key 为 provider 名称
    private final Map<String, AdPlatformConnector> connectors;

    public PartnerController(PartnerService service, List<AdPlatformConnector> connectorList) {
        this.service = service;
        this.connectors = connectorList.stream()
                .collect(Collectors.toUnmodifiableMap(AdPlatformConnector::provider, c -> c));
    }

    /**
     * 获取全量合作方连接配置列表
     * GET /api/v1/partners
     */
    @GetMapping
    public List<PartnerConnection> list() {
        return service.list();
    }

    /**
     * 注册创建新合作方连接
     * POST /api/v1/partners
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerConnection create(@Valid @RequestBody PartnerConnection input) {
        return service.create(input);
    }

    /**
     * 切换合作方连接状态
     * POST /api/v1/partners/{id}/status/{status}
     */
    @PostMapping("/{id}/status/{status}")
    public PartnerConnection status(@PathVariable String id, @PathVariable ConnectionStatus status) {
        return service.setStatus(id, status);
    }

    /**
     * 手动触发指定合作方连接的异步数据同步任务
     * POST /api/v1/partners/{id}/sync
     */
    @PostMapping("/{id}/sync")
    public CompletableFuture<AdPlatformConnector.SyncResult> sync(@PathVariable String id) {
        PartnerConnection partner = service.get(id);
        String provider = partner.settings().getOrDefault("provider", "");
        AdPlatformConnector connector = connectors.get(provider);
        if (connector == null) {
            throw new IllegalArgumentException("No connector available for provider: " + provider);
        }
        return connector.sync(partner);
    }
}
