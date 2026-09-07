package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.service.AffiliateAntiFraudEngine;
import com.affiliate.platform.affiliate.service.ClickTrackerService;
import com.affiliate.platform.affiliate.service.OfferService;
import com.affiliate.platform.affiliate.service.SubIdAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

/**
 * 渠道推广点击接收控制器 (Affiliate Click Inbound Controller)
 * <p>
 * 接收形如：
 * `GET /affiliate/click?offer_id=101&aff_id=202&sub1=fb&sub2=campaign_a`
 * 1. 验证 Offer 有效性及 Cap 配额，如超限自动路由至 Fallback Offer；
 * 2. 拦截单 IP 泛洪点击；
 * 3. 沉淀会话存根，记录 Sub-ID 流式报表；
 * 4. 执行 HTTP 302 重定向至广告主落地页。
 */
@RestController
public class AffiliateClickController {

    private final OfferService offerService;
    private final ClickTrackerService clickTracker;
    private final AffiliateAntiFraudEngine antiFraudEngine;
    private final SubIdAnalyticsService analyticsService;

    public AffiliateClickController(
            OfferService offerService,
            ClickTrackerService clickTracker,
            AffiliateAntiFraudEngine antiFraudEngine,
            SubIdAnalyticsService analyticsService
    ) {
        this.offerService = offerService;
        this.clickTracker = clickTracker;
        this.antiFraudEngine = antiFraudEngine;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/affiliate/click")
    public ResponseEntity<Void> handleClick(
            @RequestParam(name = "offer_id") String offerId,
            @RequestParam(name = "aff_id") String affiliateId,
            @RequestParam(name = "sub1", required = false) String sub1,
            @RequestParam(name = "sub2", required = false) String sub2,
            @RequestParam(name = "sub3", required = false) String sub3,
            @RequestParam(name = "sub4", required = false) String sub4,
            @RequestParam(name = "sub5", required = false) String sub5,
            @RequestParam(name = "country", required = false, defaultValue = "US") String country,
            @RequestParam(name = "device_type", required = false, defaultValue = "1") int deviceType,
            HttpServletRequest request
    ) {
        String clientIp = request != null ? request.getRemoteAddr() : "127.0.0.1";
        String userAgent = request != null ? request.getHeader("User-Agent") : "Mozilla/5.0";

        // 1. 单 IP 高频点击防刷质检 (单 IP 每分钟上限 60 次)
        if (!antiFraudEngine.checkClickFrequency(clientIp, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        // 2. 解析可用 Offer（若超限自动解析 Fallback）
        String todayKey = LocalDate.now().toString();
        Offer targetOffer = offerService.resolveActiveOfferWithFallback(offerId, todayKey);
        if (targetOffer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 3. 点击追踪与落地页宏替换
        ClickTrackerService.ClickTrackingResult trackingResult = clickTracker.trackClick(
                targetOffer, affiliateId, sub1, sub2, sub3, sub4, sub5,
                clientIp, userAgent, country, deviceType
        );

        // 4. 异步累加 Sub-ID 流式报表点击数
        analyticsService.recordClick(affiliateId, sub1);

        // 5. 执行 HTTP 302 重定向
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(trackingResult.redirectUrl()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
