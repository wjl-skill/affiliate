package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.OfferTierPayout;
import com.affiliate.platform.affiliate.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateTrackingAndPostbackTest {

    private OfferService offerService;
    private ClickTrackerService clickTracker;
    private AffiliateAntiFraudEngine antiFraudEngine;
    private PublisherPostbackDispatcher postbackDispatcher;
    private S2sPostbackService postbackService;

    @BeforeEach
    void setUp() {
        offerService = new OfferService();
        clickTracker = new ClickTrackerService();
        antiFraudEngine = new AffiliateAntiFraudEngine();
        postbackDispatcher = new PublisherPostbackDispatcher();
        postbackService = new S2sPostbackService(clickTracker, offerService, antiFraudEngine, postbackDispatcher);
    }

    @Test
    void clickTrackingAndMacroReplacement() {
        // 创建 Offer：落地页带有宏变量
        Offer offer = new Offer(
                "off-101",
                "tenant-1",
                "adv-nike",
                "Nike Summer Sale",
                "https://nike.com/buy?click_id={click_id}&sub1={sub1}&sub2={sub2}",
                Offer.PayoutType.CPA,
                new BigDecimal("5.00"),
                new BigDecimal("8.00"),
                Offer.Status.ACTIVE,
                100,
                new BigDecimal("1000.00"),
                null,
                Set.of("US"),
                Set.of(1),
                null,
                Instant.now()
        );
        offerService.save(offer);

        // 发起点击
        ClickTrackerService.ClickTrackingResult result = clickTracker.trackClick(
                offer, "aff-888", "fb_ads", "campaign_01", null, null, null,
                "1.2.3.4", "Mozilla/5.0", "US", 1
        );

        assertNotNull(result.clickId());
        assertTrue(result.redirectUrl().contains("click_id=" + result.clickId()));
        assertTrue(result.redirectUrl().contains("sub1=fb_ads"));
        assertTrue(result.redirectUrl().contains("sub2=campaign_01"));

        // 验证会话存根已保存
        assertNotNull(clickTracker.findSession(result.clickId()));
    }

    @Test
    void s2sPostbackAttributionAndTierPayout() {
        // 注册标准渠道与 VIP 渠道
        AffiliatePartner standardAff = new AffiliatePartner("aff-standard", "tenant-1", "Standard Media",
                AffiliatePartner.Status.ACTIVE, AffiliatePartner.Tier.STANDARD,
                "https://standard-media.com/pb?click_id={click_id}&payout={payout}&sub1={sub1}",
                AffiliatePartner.PaymentTerm.NET_30, new BigDecimal("50.00"), Instant.now());

        AffiliatePartner vipAff = new AffiliatePartner("aff-vip", "tenant-1", "VIP Whale Media",
                AffiliatePartner.Status.ACTIVE, AffiliatePartner.Tier.VIP,
                "https://vip-media.com/pb?click_id={click_id}&payout={payout}&sub1={sub1}",
                AffiliatePartner.PaymentTerm.NET_7, new BigDecimal("100.00"), Instant.now());

        postbackService.registerPartner(standardAff);
        postbackService.registerPartner(vipAff);

        // 创建 Offer：默认佣金 5.00，平台应收 8.00
        Offer offer = new Offer("off-202", "tenant-1", "adv-finance", "Credit Card CPA",
                "https://card.com/apply?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("5.00"), new BigDecimal("8.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        offerService.save(offer);

        // 为 VIP 等级配置专属阶梯加价：佣金 7.50，平台应收 10.00
        offerService.addTierPayout(new OfferTierPayout("off-202", null, AffiliatePartner.Tier.VIP, new BigDecimal("7.50"), new BigDecimal("10.00")));

        // 1. 标准渠道点击与转化 (设置 10 秒后转化，满足正常 CTIT 要求)
        ClickTrackerService.ClickTrackingResult stdClick = clickTracker.trackClick(offer, "aff-standard", "google", null, null, null, null, "1.1.1.1", "UA", "US", 1);
        Conversion stdConv = postbackService.processPostback(stdClick.clickId(), "order-001", BigDecimal.ZERO, Instant.now().plusSeconds(10));

        assertEquals(Conversion.Status.PENDING, stdConv.status());
        assertEquals(new BigDecimal("5.00"), stdConv.payout());
        assertEquals(new BigDecimal("8.00"), stdConv.revenue());

        // 2. VIP 渠道点击与转化 -> 自动享受阶梯加价
        ClickTrackerService.ClickTrackingResult vipClick = clickTracker.trackClick(offer, "aff-vip", "tiktok", null, null, null, null, "2.2.2.2", "UA", "US", 1);
        Conversion vipConv = postbackService.processPostback(vipClick.clickId(), "order-002", BigDecimal.ZERO, Instant.now().plusSeconds(10));

        assertEquals(Conversion.Status.PENDING, vipConv.status());
        assertEquals(new BigDecimal("7.50"), vipConv.payout());
        assertEquals(new BigDecimal("10.00"), vipConv.revenue());

        // 3. 验证下游渠道 Postback 分发日志记录
        List<PublisherPostbackDispatcher.PostbackDeliveryLog> logs = postbackDispatcher.getDeliveryLogs();
        assertEquals(2, logs.size());
        assertTrue(logs.get(0).targetUrl().contains("payout=5.00"));
        assertTrue(logs.get(1).targetUrl().contains("payout=7.50"));
        assertTrue(logs.get(1).targetUrl().contains("sub1=tiktok"));
    }

    @Test
    void capExceededFallbackRouting() {
        // 创建主 Offer（Cap 限额为 1 单），配置 Fallback Offer
        Offer fallback = new Offer("off-fallback", "tenant-1", "adv-1", "Fallback Game",
                "https://game.com/land?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("2.00"), new BigDecimal("3.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        offerService.save(fallback);

        Offer primary = new Offer("off-primary", "tenant-1", "adv-1", "Limited Luxury Offer",
                "https://luxury.com/land?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("10.00"), new BigDecimal("15.00"), Offer.Status.ACTIVE,
                1, null, "off-fallback", Set.of("US"), Set.of(1), null, Instant.now());
        offerService.save(primary);

        String today = "2026-09-03";

        // 第 1 次使用 -> 尚未超限 -> 命中 primary
        Offer chosen1 = offerService.resolveActiveOfferWithFallback("off-primary", today);
        assertEquals("off-primary", chosen1.id());

        // 发生一笔转化，Cap 耗尽
        offerService.incrementAndCheckCap("off-primary", today);

        // 第 2 次解析 -> 已超限 -> 自动路由至 off-fallback
        Offer chosen2 = offerService.resolveActiveOfferWithFallback("off-primary", today);
        assertEquals("off-fallback", chosen2.id());
    }
}
