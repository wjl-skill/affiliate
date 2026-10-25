package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.OfferSupplementLog;
import com.affiliate.platform.affiliate.domain.OfferSupplementRule;
import com.affiliate.platform.affiliate.service.OfferSupplementService;
import com.affiliate.platform.affiliate.service.ParquetUserProfileReader;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Offer 流量补量与归因平台 (AppsFlyer / Adjust) 推送管理控制器
 */
@RestController
@RequestMapping("/api/v1/affiliate/offers/supplement")
public class OfferSupplementController {

    private final OfferSupplementService supplementService;

    public OfferSupplementController(OfferSupplementService supplementService) {
        this.supplementService = supplementService;
    }

    private String resolveTenant(String headerTenant, String bodyTenant) {
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant;
        }
        if (bodyTenant != null && !bodyTenant.isBlank()) {
            return bodyTenant;
        }
        String ctx = TenantContext.get();
        return (ctx != null && !ctx.isBlank()) ? ctx : "default";
    }

    /**
     * 预览存储桶或本地路径中的 Parquet 用户信息文件 (POST 方式)
     */
    @PostMapping("/preview")
    public ResponseEntity<ParquetUserProfileReader.ParquetReadBatch> previewParquetProfiles(
            @RequestBody PreviewRequest req
    ) {
        var batch = supplementService.previewProfiles(
                req.storageConfigId(),
                req.objectKeyOrPath(),
                req.osFilter(),
                req.offset() != null ? req.offset() : 0,
                req.limit() != null ? req.limit() : 10
        );
        return ResponseEntity.ok(batch);
    }

    /**
     * 预览存储桶或本地路径中的 Parquet 用户信息文件 (GET 方式兼容)
     */
    @GetMapping({"/preview", "/preview-parquet"})
    public ResponseEntity<ParquetUserProfileReader.ParquetReadBatch> previewParquetProfilesGet(
            @RequestParam(value = "storageConfigId", required = false) String storageConfigId,
            @RequestParam(value = "objectKeyOrPath", required = false) String objectKeyOrPath,
            @RequestParam(value = "osFilter", required = false, defaultValue = "ALL") String osFilter,
            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(supplementService.previewProfiles(storageConfigId, objectKeyOrPath, osFilter, offset, limit));
    }

    /**
     * 查询 Offer 补量规则列表
     */
    @GetMapping("/rules")
    public ResponseEntity<List<OfferSupplementRule>> listRules(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestParam(value = "offerId", required = false) String offerId
    ) {
        String tenantId = resolveTenant(tenantHeader, null);
        return ResponseEntity.ok(supplementService.listRules(tenantId, offerId));
    }

    /**
     * 创建或更新 Offer 级补量规则（支持定时自动补量与游标续传）
     */
    @PostMapping("/rules")
    public ResponseEntity<OfferSupplementRule> saveRule(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestBody SaveRuleRequest req
    ) {
        String tenantId = resolveTenant(tenantHeader, req.tenantId());
        OfferSupplementRule rule = new OfferSupplementRule(
                req.id(),
                tenantId,
                req.offerId(),
                req.affiliateId(),
                req.mmpPlatform(),
                req.trackingUrlTemplate(),
                req.storageConfigId(),
                req.objectKeyOrPath(),
                req.osFilter(),
                req.batchSize() != null ? req.batchSize() : 50,
                req.qpsLimit() != null ? req.qpsLimit() : 20,
                Boolean.TRUE.equals(req.autoEnabled()),
                req.cronIntervalMinutes() != null ? req.cronIntervalMinutes() : 30,
                req.lastCursorOffset() != null ? req.lastCursorOffset() : 0,
                0L,
                req.status() != null ? req.status() : "ACTIVE",
                null,
                Instant.now(),
                Instant.now()
        );
        return ResponseEntity.ok(supplementService.saveRule(rule));
    }

    /**
     * 删除补量规则
     */
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable("ruleId") String ruleId) {
        boolean deleted = supplementService.deleteRule(ruleId);
        return ResponseEntity.ok(Map.of("success", deleted, "ruleId", ruleId));
    }

    /**
     * 立即触发执行一批 Offer 补量任务（解析 Parquet -> 记录 ClickSession -> 替换并补齐参数 -> HTTP 推送 AF/Adjust）
     */
    @PostMapping("/trigger")
    public ResponseEntity<OfferSupplementService.SupplementExecutionResult> triggerSupplement(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestBody OfferSupplementService.SupplementTriggerRequest req
    ) {
        String tenantId = resolveTenant(tenantHeader, req.tenantId());
        var normalizedReq = new OfferSupplementService.SupplementTriggerRequest(
                tenantId,
                req.ruleId(),
                req.offerId(),
                req.affiliateId(),
                req.mmpPlatform(),
                req.trackingUrlTemplate(),
                req.storageConfigId(),
                req.objectKeyOrPath(),
                req.osFilter(),
                req.batchSize(),
                req.qpsLimit(),
                req.startOffset()
        );
        return ResponseEntity.ok(supplementService.executeSupplementBatch(normalizedReq));
    }

    /**
     * 查询补量点击与归因平台 HTTP 推送审计日志
     */
    @GetMapping("/logs")
    public ResponseEntity<List<OfferSupplementLog>> listSupplementLogs(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestParam(value = "offerId", required = false) String offerId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit
    ) {
        String tenantId = resolveTenant(tenantHeader, null);
        return ResponseEntity.ok(supplementService.listLogs(tenantId, offerId, limit));
    }

    /**
     * 查询 Offer 补量汇总指标统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSupplementStats(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestParam(value = "offerId", required = false) String offerId
    ) {
        String tenantId = resolveTenant(tenantHeader, null);
        return ResponseEntity.ok(supplementService.getStats(tenantId, offerId));
    }

    public record PreviewRequest(
            String storageConfigId,
            String objectKeyOrPath,
            String osFilter,
            Integer offset,
            Integer limit
    ) {}

    public record SaveRuleRequest(
            String id,
            String tenantId,
            String offerId,
            String affiliateId,
            String mmpPlatform,
            String trackingUrlTemplate,
            String storageConfigId,
            String objectKeyOrPath,
            String osFilter,
            Integer batchSize,
            Integer qpsLimit,
            Boolean autoEnabled,
            Integer cronIntervalMinutes,
            Integer lastCursorOffset,
            String status
    ) {}
}
