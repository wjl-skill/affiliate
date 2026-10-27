package com.affiliate.platform.web;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.integration.AdPlatformConnector;
import com.affiliate.platform.integration.MediaPlatformConnectorRegistry;
import com.affiliate.platform.tenant.PartnerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    // 媒体与变现网络连接器注册表
    private final MediaPlatformConnectorRegistry registry;

    @Autowired
    public PartnerController(PartnerService service, MediaPlatformConnectorRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    public PartnerController(PartnerService service, List<AdPlatformConnector> connectorList) {
        this(service, new MediaPlatformConnectorRegistry(connectorList));
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
        AdPlatformConnector connector = registry.getConnector(provider);
        return connector.sync(partner);
    }
}
