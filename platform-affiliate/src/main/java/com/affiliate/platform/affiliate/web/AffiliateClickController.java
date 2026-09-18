package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.SmartLink;
import com.affiliate.platform.affiliate.service.AffiliateAntiFraudEngine;
import com.affiliate.platform.affiliate.service.ClickTrackerService;
import com.affiliate.platform.affiliate.service.OfferService;
import com.affiliate.platform.affiliate.service.SubIdAnalyticsService;
import com.affiliate.platform.affiliate.service.TdsRouter;
import com.affiliate.platform.entity.SmartLinkEntity;
import com.affiliate.platform.mapper.SmartLinkMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 或 SmartLink 分流链接：
 * `GET /affiliate/click?smartlink_id=sl_101&aff_id=202&sub1=fb`
 * 1. 验证 Offer 有效性及 Cap 配额，如超限自动路由至 Fallback Offer；SmartLink 则经 TDS 引擎优选候选；
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
    private final TdsRouter tdsRouter;
    private final SmartLinkMapper smartLinkMapper;

    @Autowired
    public AffiliateClickController(
            OfferService offerService,
            ClickTrackerService clickTracker,
            AffiliateAntiFraudEngine antiFraudEngine,
            SubIdAnalyticsService analyticsService,
            TdsRouter tdsRouter,
            @Autowired(required = false) SmartLinkMapper smartLinkMapper
    ) {
        this.offerService = offerService;
        this.clickTracker = clickTracker;
        this.antiFraudEngine = antiFraudEngine;
        this.analyticsService = analyticsService;
        this.tdsRouter = tdsRouter;
        this.smartLinkMapper = smartLinkMapper;
    }

    @GetMapping("/affiliate/click")
    public ResponseEntity<Void> handleClick(
            @RequestParam(name = "offer_id", required = false) String offerId,
            @RequestParam(name = "smartlink_id", required = false) String smartLinkId,
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
        boolean hasOffer = offerId != null && !offerId.isBlank();
        boolean hasSmartLink = smartLinkId != null && !smartLinkId.isBlank();
        if (!hasOffer && !hasSmartLink) {
            return ResponseEntity.badRequest().build();
        }

        String clientIp = request != null ? request.getRemoteAddr() : "127.0.0.1";
        String userAgent = request != null ? request.getHeader("User-Agent") : "Mozilla/5.0";

        // 1. 单 IP 高频点击防刷质检 (单 IP 每分钟上限 60 次)
        if (!antiFraudEngine.checkClickFrequency(clientIp, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        // 2. 解析可用 Offer（SmartLink 走 TDS 智能路由；直链超限自动解析 Fallback）
        String todayKey = LocalDate.now().toString();
        Offer targetOffer;
        if (hasSmartLink) {
            SmartLink link = findSmartLink(smartLinkId);
            if (link == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            targetOffer = tdsRouter.route(link, country, deviceType, todayKey);
        } else {
            targetOffer = offerService.resolveActiveOfferWithFallback(offerId, todayKey);
        }
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

    private SmartLink findSmartLink(String smartLinkId) {
        if (smartLinkMapper == null) {
            return null;
        }
        SmartLinkEntity entity = smartLinkMapper.selectById(smartLinkId);
        if (entity == null) {
            return null;
        }
        return new SmartLink(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getCategory(),
                entity.getTargetOfferIds() != null ? entity.getTargetOfferIds() : java.util.List.of(),
                entity.getRoutingStrategy() != null
                        ? SmartLink.RoutingStrategy.valueOf(entity.getRoutingStrategy())
                        : SmartLink.RoutingStrategy.HIGHEST_EPC,
                entity.getFallbackOfferId(),
                entity.getCreatedAt()
        );
    }
}
