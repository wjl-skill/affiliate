package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.service.S2sPostbackService;
import com.affiliate.platform.affiliate.service.SubIdAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 广告主 S2S 服务端转化回传接收控制器 (Advertiser S2S Postback Controller)
 * <p>
 * 接收形如：
 * `GET/POST /affiliate/postback?click_id=c_xxx&txid=order_888&sale_amount=100.00`
 * 1. 归因对齐原始点击会话；
 * 2. 检查 CTIT 转化耗时与交易号幂等；
 * 3. 记录转化事实并触发渠道下游 Postback 通知分发；
 * 4. 累加 Sub-ID 维度转化及佣金统计；
 * 5. 返回标准 JSON 响应。
 */
@RestController
public class S2sPostbackController {

    private final S2sPostbackService postbackService;
    private final SubIdAnalyticsService analyticsService;

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
            @RequestParam(name = "sale_amount", required = false, defaultValue = "0.00") BigDecimal saleAmount
    ) {
        return process(clickId, txId, saleAmount);
    }

    @PostMapping("/affiliate/postback")
    public ResponseEntity<Map<String, Object>> handlePostPostback(
            @RequestParam(name = "click_id") String clickId,
            @RequestParam(name = "txid") String txId,
            @RequestParam(name = "sale_amount", required = false, defaultValue = "0.00") BigDecimal saleAmount
    ) {
        return process(clickId, txId, saleAmount);
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
