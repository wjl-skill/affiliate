package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.SmartLink;
import com.affiliate.platform.affiliate.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateAntiFraudAndSettlementTest {

    private OfferService offerService;
    private ClickTrackerService clickTracker;
    private AffiliateAntiFraudEngine antiFraudEngine;
    private PublisherPostbackDispatcher postbackDispatcher;
    private S2sPostbackService postbackService;
    private AffiliateSettlementService settlementService;
    private SubIdAnalyticsService analyticsService;
    private TdsRouter tdsRouter;

    @BeforeEach
    void setUp() {
        offerService = new OfferService();
        clickTracker = new ClickTrackerService();
        antiFraudEngine = new AffiliateAntiFraudEngine();
        postbackDispatcher = new PublisherPostbackDispatcher();
        postbackService = new S2sPostbackService(clickTracker, offerService, antiFraudEngine, postbackDispatcher);
        settlementService = new AffiliateSettlementService(postbackService);
        analyticsService = new SubIdAnalyticsService();
        tdsRouter = new TdsRouter(offerService);
    }

    @Test
    void ctitFraudDetectionAndDuplicateTxId() {
        Offer offer = new Offer("off-fraud-test", "tenant-1", "adv-1", "Insurance CPA",
                "https://insure.com/quote?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("20.00"), new BigDecimal("30.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        offerService.save(offer);

        // 1. 模拟点击事件
        ClickTrackerService.ClickTrackingResult click = clickTracker.trackClick(offer, "aff-bot", "sub", null, null, null, null, "3.3.3.3", "UA", "US", 1);

        // 2. 仅 1 秒后即触发转化 (CTIT < 3s) -> 触发极速刷量/点击注入风控
        Instant fastConversionTime = click.session().createdAt().plusSeconds(1);
        Conversion fastConv = postbackService.processPostback(click.clickId(), "tx-bot-01", BigDecimal.ZERO, fastConversionTime);

        assertEquals(Conversion.Status.FRAUD_SUSPECTED, fastConv.status());
        assertEquals("FAST_CONVERSION_CTIT_UNDER_3S", fastConv.rejectionReason());

        // 3. 重复上报相同 txid -> 判定为重放攻击拦截
        Conversion dupConv = postbackService.processPostback(click.clickId(), "tx-bot-01", BigDecimal.ZERO, fastConversionTime.plusSeconds(10));
        assertEquals(Conversion.Status.REJECTED, dupConv.status());
        assertEquals("DUPLICATE_TRANSACTION_ID", dupConv.rejectionReason());
    }

    @Test
    void smartLinkTdsDynamicEpcRouting() {
        // 创建两个符合条件的竞品 Offer
        Offer offerA = new Offer("off-A", "tenant-1", "adv-1", "E-Comm Store A",
                "https://a.com?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("5.00"), new BigDecimal("8.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now());

        Offer offerB = new Offer("off-B", "tenant-1", "adv-2", "E-Comm Store B",
                "https://b.com?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("10.00"), new BigDecimal("15.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now());

        offerService.save(offerA);
        offerService.save(offerB);

        // 注入实时 EPC 数据：Offer A EPC=0.50, Offer B EPC=2.20
        tdsRouter.updateOfferEpc("off-A", new BigDecimal("0.50"));
        tdsRouter.updateOfferEpc("off-B", new BigDecimal("2.20"));

        SmartLink smartLink = new SmartLink(
                "smart-ecom",
                "tenant-1",
                "Global E-Commerce SmartLink",
                "E-Commerce",
                List.of("off-A", "off-B"),
                SmartLink.RoutingStrategy.HIGHEST_EPC,
                null,
                Instant.now()
        );

        // TDS 执行动态路由 -> 自动优选产出更高的 Offer B
        Offer routed = tdsRouter.route(smartLink, "US", 1, "2026-09-03");
        assertNotNull(routed);
        assertEquals("off-B", routed.id());
    }

    @Test
    void commissionApprovalAndInvoiceThreshold() {
        AffiliatePartner partner = new AffiliatePartner(
                "aff-good",
                "tenant-1",
                "Reliable Partner",
                AffiliatePartner.Status.ACTIVE,
                AffiliatePartner.Tier.GOLD,
                null,
                AffiliatePartner.PaymentTerm.NET_15,
                new BigDecimal("100.00"), // 100 美元最低提现门槛
                Instant.now()
        );
        postbackService.registerPartner(partner);

        Offer offer = new Offer("off-regular", "tenant-1", "adv-1", "Game Install CPI",
                "https://game.com?click_id={click_id}", Offer.PayoutType.CPI,
                new BigDecimal("40.00"), new BigDecimal("60.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        offerService.save(offer);

        // 第 1 笔转化 (40.00)
        ClickTrackerService.ClickTrackingResult c1 = clickTracker.trackClick(offer, "aff-good", "ch1", null, null, null, null, "1.1.1.1", "UA", "US", 1);
        Conversion conv1 = postbackService.processPostback(c1.clickId(), "tx-c1", BigDecimal.ZERO, Instant.now().plusSeconds(10));

        // 初始处于 PENDING 状态
        assertEquals(Conversion.Status.PENDING, conv1.status());

        // 审核通过该转化
        Conversion approved1 = settlementService.approve(conv1.id());
        assertEquals(Conversion.Status.APPROVED, approved1.status());

        // 尝试出账：累计仅 40.00 < 100.00 起提门槛 -> 无法出账
        Optional<AffiliateSettlementService.AffiliateInvoice> inv1 = settlementService.generateInvoice("aff-good", partner);
        assertTrue(inv1.isEmpty());

        // 第 2 笔转化 (40.00) & 第 3 笔转化 (40.00)
        ClickTrackerService.ClickTrackingResult c2 = clickTracker.trackClick(offer, "aff-good", "ch1", null, null, null, null, "1.1.1.1", "UA", "US", 1);
        Conversion conv2 = postbackService.processPostback(c2.clickId(), "tx-c2", BigDecimal.ZERO, Instant.now().plusSeconds(10));
        settlementService.approve(conv2.id());

        ClickTrackerService.ClickTrackingResult c3 = clickTracker.trackClick(offer, "aff-good", "ch1", null, null, null, null, "1.1.1.1", "UA", "US", 1);
        Conversion conv3 = postbackService.processPostback(c3.clickId(), "tx-c3", BigDecimal.ZERO, Instant.now().plusSeconds(10));
        settlementService.approve(conv3.id());

        // 再次出账：累计 120.00 >= 100.00 -> 成功生成正式账单
        Optional<AffiliateSettlementService.AffiliateInvoice> inv2 = settlementService.generateInvoice("aff-good", partner);
        assertTrue(inv2.isPresent());
        assertEquals(new BigDecimal("120.00"), inv2.get().amount());
        assertEquals(3, inv2.get().conversionCount());
        assertEquals(AffiliatePartner.PaymentTerm.NET_15, inv2.get().paymentTerm());
    }

    @Test
    void subIdAnalyticsPerformanceCalculation() {
        // 模拟渠道 aff-999 在 sub1=google_ads 产生 200 次点击，10 次转化，佣金 50.00，收入 80.00
        for (int i = 0; i < 200; i++) {
            analyticsService.recordClick("aff-999", "google_ads");
        }
        for (int i = 0; i < 10; i++) {
            analyticsService.recordConversion("aff-999", "google_ads", new BigDecimal("5.00"), new BigDecimal("8.00"));
        }

        SubIdAnalyticsService.SubIdPerformance perf = analyticsService.getPerformance("aff-999", "google_ads");

        assertEquals(200, perf.clicks());
        assertEquals(10, perf.conversions());
        assertEquals(new BigDecimal("50.00"), perf.totalPayout());
        assertEquals(new BigDecimal("80.00"), perf.totalRevenue());

        // CR% = 10 / 200 * 100 = 5.0%
        assertEquals(5.0, perf.crPercent(), 0.01);

        // EPC = 50.00 / 200 = 0.2500
        assertEquals(new BigDecimal("0.2500"), perf.epc());

        // RPC = 80.00 / 200 = 0.4000
        assertEquals(new BigDecimal("0.4000"), perf.rpc());

        // Margin = 80.00 - 50.00 = 30.00
        assertEquals(new BigDecimal("30.0000"), perf.margin());
    }
}
