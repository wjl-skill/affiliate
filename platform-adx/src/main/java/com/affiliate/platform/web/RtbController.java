package com.affiliate.platform.web;

import com.affiliate.platform.budget.BudgetService;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.rtb.HmacTokenService;
import com.affiliate.platform.rtb.OpenRtb;
import com.affiliate.platform.rtb.OpenRtbAuctionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 实时竞价与广告追踪回调 REST 控制器 (RTB & Ad Tracking REST Controller)
 * <p>
 * 提供 OpenRTB 2.5 标准竞价入口，以及由广告前端渲染触发的胜出通知、曝光上报与点击跳转闭环端点：
 * 1. POST /rtb/openrtb/2.5/bid - 极速实时竞价出价（< 20ms）；
 * 2. GET /rtb/win - 胜出通知回调（核验 HMAC Token、转正确认预算预占并发布领域事件）；
 * 3. GET /rtb/imp - 曝光打点追踪（发布曝光事件）；
 * 4. GET /rtb/click - 点击跳转追踪（发布点击事件并 302 重定向至落地页）。
 */
@RestController
public class RtbController {

    // RTB 拍卖核心引擎
    private final OpenRtbAuctionService service;

    // HMAC 防篡改加验签工具
    private final HmacTokenService hmacService;

    // 预算管控服务
    private final BudgetService budgetService;

    // 领域事件总线
    private final EventPublisher eventPublisher;

    public RtbController(
            OpenRtbAuctionService service,
            HmacTokenService hmacService,
            BudgetService budgetService,
            EventPublisher eventPublisher
    ) {
        this.service = service;
        this.hmacService = hmacService;
        this.budgetService = budgetService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 接收 SSP 发起的标准 OpenRTB 2.5 竞价请求
     *
     * @param request OpenRTB 请求体
     * @return OpenRTB 响应体（包含各 SeatBid 出价）
     */
    @PostMapping("/rtb/openrtb/2.5/bid")
    public OpenRtb.BidResponse bid(@Valid @RequestBody OpenRtb.BidRequest request) {
        return service.bid(request);
    }

    /**
     * 广告竞价胜出通知端点 (Win Notice URL 回调)
     *
     * @param token 竞价响应中下发的带防篡改签名的加密 Token
     * @return 确认状态响应
     */
    @GetMapping("/rtb/win")
    public ResponseEntity<Map<String, Object>> winNotice(@RequestParam("token") String token) {
        // 1. 核验并解析防篡改 Token（校验签名与 1 小时有效期）
        HmacTokenService.TrackingPayload payload = hmacService.verifyAndParse(token);

        // 2. 确认预算预占（将临时预占转正为已确认状态）
        BudgetService.Reservation reservation = new BudgetService.Reservation(
                payload.reservationId(),
                payload.tenantId(),
                payload.campaignId(),
                null,
                BigDecimal.valueOf(payload.price() / 1000.0)
        );
        budgetService.confirm(reservation);

        // 3. 发布 auction.win.v1 领域事件进入可靠发件箱
        eventPublisher.publish(DomainEvent.create("auction.win.v1", payload.tenantId(), payload.auctionId(), payload));

        return ResponseEntity.ok(Map.of(
                "status", "CONFIRMED",
                "auctionId", payload.auctionId(),
                "price", payload.price()
        ));
    }

    /**
     * 广告曝光实际上屏通知端点 (Impression Tracking Pixel)
     *
     * @param token 防篡改 Token
     * @return 记录成功响应
     */
    @GetMapping("/rtb/imp")
    public ResponseEntity<Map<String, Object>> impression(@RequestParam("token") String token) {
        HmacTokenService.TrackingPayload payload = hmacService.verifyAndParse(token);
        eventPublisher.publish(DomainEvent.create("impression.recorded.v1", payload.tenantId(), payload.auctionId(), payload));
        return ResponseEntity.ok(Map.of(
                "status", "RECORDED",
                "auctionId", payload.auctionId()
        ));
    }

    /**
     * 用户点击广告落地跳转端点 (Click Redirect & Tracking)
     *
     * @param token      防篡改 Token
     * @param landingUrl 广告落地页目标 URL
     * @param response   HTTP 响应对象
     */
    @GetMapping("/rtb/click")
    public void click(
            @RequestParam("token") String token,
            @RequestParam(value = "landingUrl", defaultValue = "https://example.com") String landingUrl,
            HttpServletResponse response
    ) throws IOException {
        HmacTokenService.TrackingPayload payload = hmacService.verifyAndParse(token);
        // 发布 click.recorded.v1 领域事件
        eventPublisher.publish(DomainEvent.create("click.recorded.v1", payload.tenantId(), payload.auctionId(), payload));
        // HTTP 302 重定向至广告主落地页
        response.sendRedirect(landingUrl);
    }
}
