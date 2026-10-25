package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.service.AffiliateAntiFraudEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 4D 反欺诈安全风控规则动态配置与大盘指标 REST 控制器
 */
@RestController
@RequestMapping({"/api/v1/affiliate/anti-fraud", "/affiliate/anti-fraud"})
public class AntiFraudConfigController {

    private final AffiliateAntiFraudEngine antiFraudEngine;

    public AntiFraudConfigController(AffiliateAntiFraudEngine antiFraudEngine) {
        this.antiFraudEngine = antiFraudEngine;
    }

    /**
     * 获取当前 4D 动态风控规则
     */
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getRules() {
        var cfg = antiFraudEngine.getRuleConfig();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("minCtitSeconds", cfg.minCtitSeconds());
        resp.put("maxCtitSeconds", cfg.maxCtitSeconds());
        resp.put("enableDatacenterCheck", cfg.enableDatacenterCheck());
        resp.put("enableBotUaCheck", cfg.enableBotUaCheck());
        resp.put("ipBlacklistSize", antiFraudEngine.getIpBlacklistSize());
        resp.put("subIdBlacklistSize", antiFraudEngine.getSubIdBlacklistSize());
        return ResponseEntity.ok(resp);
    }

    /**
     * 更新 4D 动态风控规则并触发 Redis Pub/Sub 全集群广播
     */
    @PutMapping("/rules")
    public ResponseEntity<?> updateRules(@RequestBody RuleUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body cannot be null"));
        }
        if (request.minCtitSeconds() < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "minCtitSeconds must be at least 1 second"));
        }
        if (request.maxCtitSeconds() <= request.minCtitSeconds()) {
            return ResponseEntity.badRequest().body(Map.of("error", "maxCtitSeconds must be greater than minCtitSeconds"));
        }

        var newConfig = new AffiliateAntiFraudEngine.AntiFraudRuleConfig(
                request.minCtitSeconds(),
                request.maxCtitSeconds(),
                request.enableDatacenterCheck(),
                request.enableBotUaCheck()
        );

        antiFraudEngine.updateRuleConfig(newConfig);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Anti-fraud rules updated and broadcast via Redis Pub/Sub",
                "rules", newConfig
        ));
    }

    /**
     * 获取反欺诈引擎实时指标与拦截统计
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(antiFraudEngine.getMetrics());
    }

    /**
     * 动态追加 IP 黑名单
     */
    @PostMapping("/blacklist/ip")
    public ResponseEntity<?> addIpBlacklist(@RequestParam String ip) {
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "IP address cannot be empty"));
        }
        antiFraudEngine.addToBlacklist("IP", ip.trim());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "IP added to blacklist", "ip", ip.trim()));
    }

    /**
     * 动态追加 Sub-ID 黑名单
     */
    @PostMapping("/blacklist/sub-id")
    public ResponseEntity<?> addSubIdBlacklist(@RequestParam String subId) {
        if (subId == null || subId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sub-ID cannot be empty"));
        }
        antiFraudEngine.addToBlacklist("SUB_ID", subId.trim());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Sub-ID added to blacklist", "subId", subId.trim()));
    }

    public record RuleUpdateRequest(
            long minCtitSeconds,
            long maxCtitSeconds,
            boolean enableDatacenterCheck,
            boolean enableBotUaCheck
    ) {}
}
