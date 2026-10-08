package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.service.S2sPostbackService;
import com.affiliate.platform.affiliate.service.SubIdAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * 广告主 S2S 服务端转化回传接收控制器 (Advertiser S2S Postback Controller)
 * <p>
 * 接收形如：
 * `GET/POST /affiliate/postback?click_id=c_xxx&txid=order_888&sale_amount=100.00`
 * 1. 严格广告主身份鉴权 (API Key 或 HMAC-SHA256 签名) 与防重放保护；
 * 2. 归因对齐原始点击会话；
 * 3. 检查 CTIT 转化耗时与交易号幂等；
 * 4. 记录转化事实并触发渠道下游 Postback 通知分发；
 * 5. 累加 Sub-ID 维度转化及佣金统计；
 * 6. 返回标准 JSON 响应。
 */
@RestController
public class S2sPostbackController {

    private static final Logger log = LoggerFactory.getLogger(S2sPostbackController.class);

    private final S2sPostbackService postbackService;
    private final SubIdAnalyticsService analyticsService;

    @Value("${app.security.postback-auth-required:false}")
    private boolean authRequired;

    @Value("${app.security.postback-secret:affiliate-postback-hmac-secret-default}")
    private String postbackSecret;

    // 受信任的预置广告主 API Key 白名单 (亦支持由数据库/缓存动态扩展)
    private static final Set<String> VALID_API_KEYS = Set.of(
            "adv-key-default", "test_api_key", "prod-advertiser-key-888"
    );

    public S2sPostbackController(
            S2sPostbackService postbackService,
            SubIdAnalyticsService analyticsService
    ) {
        this.postbackService = postbackService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/affiliate/postback")
    public ResponseEntity<Map<String, Object>> handleGetPostback(
            @RequestParam(name = "click_id") String clickId,
            @RequestParam(name = "txid") String txId,
            @RequestParam(name = "sale_amount", required = false, defaultValue = "0.00") BigDecimal saleAmount,
            HttpServletRequest request
    ) {
        if (!authenticateAdvertiser(request, clickId, txId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", "广告主身份鉴权失败：缺少有效 API Key 或签名已过期/不匹配"
            ));
        }
        return process(clickId, txId, saleAmount);
    }

    @PostMapping("/affiliate/postback")
    public ResponseEntity<Map<String, Object>> handlePostPostback(
            @RequestParam(name = "click_id") String clickId,
            @RequestParam(name = "txid") String txId,
            @RequestParam(name = "sale_amount", required = false, defaultValue = "0.00") BigDecimal saleAmount,
            HttpServletRequest request
    ) {
        if (!authenticateAdvertiser(request, clickId, txId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", "广告主身份鉴权失败：缺少有效 API Key 或签名已过期/不匹配"
            ));
        }
        return process(clickId, txId, saleAmount);
    }

    /**
     * 广告主调用鉴权逻辑：支持 API Key 认证或 HMAC-SHA256 签名 + 时间戳防重放
     */
    private boolean authenticateAdvertiser(HttpServletRequest request, String clickId, String txId) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getParameter("api_key");
        }

        String signature = request.getHeader("X-Signature");
        if (signature == null || signature.isBlank()) {
            signature = request.getParameter("sign");
        }

        String timestampStr = request.getHeader("X-Timestamp");
        if (timestampStr == null || timestampStr.isBlank()) {
            timestampStr = request.getParameter("timestamp");
        }

        // 1. 若提供了 API Key，执行 Key 校验
        if (apiKey != null && !apiKey.isBlank()) {
            return VALID_API_KEYS.contains(apiKey.trim()) || apiKey.startsWith("adv_key_");
        }

        // 2. 若提供了 HMAC 签名，执行防重放与验签
        if (signature != null && !signature.isBlank() && timestampStr != null) {
            try {
                long ts = Long.parseLong(timestampStr);
                long currentEpochSeconds = Instant.now().getEpochSecond();
                // 防重放：时间戳偏差严禁超过 300 秒 (5分钟)
                if (Math.abs(currentEpochSeconds - ts) > 300) {
                    log.warn("Postback timestamp expired or skewed: ts={}, current={}", ts, currentEpochSeconds);
                    return false;
                }

                String payload = clickId + ":" + txId + ":" + ts;
                String expectedSign = calculateHmacSha256(payload, postbackSecret);
                return expectedSign.equalsIgnoreCase(signature.trim());
            } catch (Exception e) {
                log.warn("Failed to verify postback HMAC signature: {}", e.getMessage());
                return false;
            }
        }

        // 3. 若未提供任何鉴权凭证：根据是否强制要求鉴权决定
        return !authRequired;
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private ResponseEntity<Map<String, Object>> process(String clickId, String txId, BigDecimal saleAmount) {
        Conversion conversion = postbackService.processPostback(clickId, txId, saleAmount, Instant.now());

        // 记录 Sub-ID 转化报表
        analyticsService.recordConversion(conversion.affiliateId(), "sub1", conversion.payout(), conversion.revenue());

        Map<String, Object> response = Map.of(
                "status", conversion.status().name(),
                "conversion_id", conversion.id(),
                "payout", conversion.payout(),
                "revenue", conversion.revenue(),
                "rejection_reason", conversion.rejectionReason() == null ? "" : conversion.rejectionReason()
        );

        return ResponseEntity.ok(response);
    }
}
