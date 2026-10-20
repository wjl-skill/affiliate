package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.SkanAttributionResult;
import com.affiliate.platform.affiliate.domain.SkanPostbackPayload;
import com.affiliate.platform.affiliate.service.SkanAttributionEngine;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Apple SKAdNetwork 4.0 / AdAttributionKit 回传接收与查询控制器
 */
@RestController
@RequestMapping("/affiliate/skan")
public class SkanPostbackController {

    private final SkanAttributionEngine skanEngine;

    public SkanPostbackController(SkanAttributionEngine skanEngine) {
        this.skanEngine = skanEngine;
    }

    /**
     * 接收 Apple 官方规范的 S2S 归因回传
     */
    @PostMapping("/postback")
    public ResponseEntity<Map<String, Object>> receiveSkanPostback(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @Valid @RequestBody SkanPostbackPayload payload
    ) {
        String tenantId = (tenantHeader != null && !tenantHeader.isBlank())
                ? tenantHeader
                : (TenantContext.get() != null ? TenantContext.get() : "default");

        SkanAttributionResult result = skanEngine.processPostback(tenantId, payload);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "transactionId", result.transactionId(),
                "campaignId", result.campaignId(),
                "postbackSequenceIndex", result.postbackSequenceIndex(),
                "estimatedRevenue", result.estimatedRevenue(),
                "status", result.status()
        ));
    }

    /**
     * 查询指定广告网络名下的 SKAN 归因记录
     */
    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getSkanReports(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestParam(value = "ad_network_id", required = false, defaultValue = "") String adNetworkId
    ) {
        String tenantId = (tenantHeader != null && !tenantHeader.isBlank())
                ? tenantHeader
                : (TenantContext.get() != null ? TenantContext.get() : "default");

        List<SkanAttributionResult> list = skanEngine.listByAdNetwork(tenantId, adNetworkId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "adNetworkId", adNetworkId,
                "count", list.size(),
                "records", list
        ));
    }
}
