package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.*;
import com.affiliate.platform.affiliate.service.*;
import com.affiliate.platform.affiliate.service.ConversionAttributionService.AttributionCredit;
import com.affiliate.platform.affiliate.service.ConversionAttributionService.TouchPoint;
import com.affiliate.platform.affiliate.service.ConversionAttributionService.TouchPointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 1 升级验证：跨国概率归因修复、反欺诈三态闭环、异步回传与确定性归因测试")
class Phase1TrackingAndAttributionUpgradeTest {

    private OfferService offerService;
    private ClickTrackerService clickTracker;
    private AffiliateAntiFraudEngine antiFraudEngine;
    private PublisherPostbackDispatcher postbackDispatcher;
    private ProbabilisticAttributionEngine probabilisticEngine;
    private S2sPostbackService postbackService;
    private AffiliateSettlementService settlementService;

    @BeforeEach
    void setUp() {
        offerService = new OfferService();
        probabilisticEngine = new ProbabilisticAttributionEngine();
        clickTracker = new ClickTrackerService(null, null, probabilisticEngine);
        antiFraudEngine = new AffiliateAntiFraudEngine();
        postbackDispatcher = new PublisherPostbackDispatcher();
        postbackService = new S2sPostbackService(
                clickTracker, offerService, antiFraudEngine, postbackDispatcher,
                null, null, probabilisticEngine
        );
        settlementService = new AffiliateSettlementService(postbackService);
    }

    @Test
    @DisplayName("1. 非 US 流量 (CN/GB) 无 click_id 概率指纹归因动态解析国家修复验证")
    void testNonUsProbabilisticAttributionDynamicCountryResolution() {
        Offer cnOffer = new Offer(
                "off-cn-prob", "tenant-1", "adv-cn", "CN E-Commerce CPA",
                "https://shop.cn/item?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("12.00"), new BigDecimal("18.00"), Offer.Status.ACTIVE,
                100, null, null, Set.of("CN"), Set.of(1), null, Instant.now()
        );
        offerService.save(cnOffer);

        // 模拟中国区用户产生点击（同 C 段 IP 114.114.114.10 -> 转化时变为 114.114.114.88，依赖国家匹配+0.12使总分达到 0.25+0.35+0.12+0.08=0.80）
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";
        ClickTrackerService.ClickTrackingResult click = clickTracker.trackClick(
                cnOffer, "aff-cn-01", "wechat_moment", null, null, null, null,
                "114.114.114.10", ua, "CN", 1
        );
        assertNotNull(click.clickId());

        // S2S Postback 未携带 click_id，且 IP 在同一 C 段切换为 114.114.114.88
        // 修复前硬编码 "US" 导致国家不匹配得分仅 0.68 (<0.80) 归因失败；修复后动态解析 "CN" 得分 0.80 成功归因
        Conversion conv = postbackService.processPostback(
                null, "tx-cn-prob-001", BigDecimal.ZERO, null, "off-cn-prob",
                "114.114.114.88", ua, 1, Instant.now().plusSeconds(15)
        );

        assertNotEquals(Conversion.Status.REJECTED, conv.status(), "非 US 同子网概率归因应成功匹配");
        assertEquals(click.clickId(), conv.clickId());
        assertEquals("aff-cn-01", conv.affiliateId());
        assertEquals(new BigDecimal("12.00"), conv.payout());
    }

    @Test
    @DisplayName("2. 反欺诈三态闭环与下游 Postback 审批联动验证 (APPROVED 直发 / PENDING 挂起 -> 审核后补发)")
    void testAutoApproveLowRiskAndPendingApprovalPostbackClosure() {
        antiFraudEngine.setAutoApproveLowRisk(true);

        AffiliatePartner partner = new AffiliatePartner(
                "aff-closure", "tenant-1", "Closure Media",
                AffiliatePartner.Status.ACTIVE, AffiliatePartner.Tier.GOLD,
                "https://partner.example.com/postback?click_id={click_id}&tx_id={tx_id}&payout={payout}",
                AffiliatePartner.PaymentTerm.NET_15, new BigDecimal("50.00"), Instant.now()
        );
        postbackService.registerPartner(partner);

        Offer offer = new Offer(
                "off-closure", "tenant-1", "adv-1", "SaaS Trial CPA",
                "https://saas.com/signup?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("15.00"), new BigDecimal("25.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now()
        );
        offerService.save(offer);

        // 场景 A: 低风险转化 (riskScore = 0 < 35) -> 自动 APPROVED 并立即触发下游 Postback
        ClickTrackerService.ClickTrackingResult lowRiskClick = clickTracker.trackClick(
                offer, "aff-closure", "sub_low", null, null, null, null,
                "198.51.100.10", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", "US", 1
        );
        Conversion lowRiskConv = postbackService.processPostback(
                lowRiskClick.clickId(), "tx-low-risk-01", BigDecimal.ZERO, null, "off-closure",
                "198.51.100.10", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", 1,
                lowRiskClick.session().createdAt().plusSeconds(20)
        );

        assertEquals(Conversion.Status.APPROVED, lowRiskConv.status());
        assertEquals(Conversion.PostbackStatus.DELIVERED, lowRiskConv.postbackStatus());
        assertEquals(1, postbackDispatcher.getDeliveryLogs().size());

        // 场景 B: 中风险灰产嫌疑转化 (AWS 机房 IP 52.10.10.1 -> riskScore = 45, 处于 [35, 70) 灰名单区间)
        // 应进入 PENDING 待人工审核状态，且暂不向下游渠道发送 Postback
        ClickTrackerService.ClickTrackingResult medRiskClick = clickTracker.trackClick(
                offer, "aff-closure", "sub_med", null, null, null, null,
                "52.10.10.1", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", "US", 1
        );
        Conversion medRiskConv = postbackService.processPostback(
                medRiskClick.clickId(), "tx-med-risk-02", BigDecimal.ZERO, null, "off-closure",
                "52.10.10.1", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", 1,
                medRiskClick.session().createdAt().plusSeconds(20)
        );

        assertEquals(Conversion.Status.PENDING, medRiskConv.status(), "中风险转化应进入 PENDING 待审状态");
        assertEquals(Conversion.PostbackStatus.PENDING, medRiskConv.postbackStatus(), "待审转化不应提前触发下游回传");
        assertEquals(1, postbackDispatcher.getDeliveryLogs().size(), "待审期间下游回传日志数不应增加");

        // 人工审核通过后，自动补发下游渠道 Postback
        Conversion approvedAfterReview = settlementService.approve(medRiskConv.id());
        assertEquals(Conversion.Status.APPROVED, approvedAfterReview.status());
        assertEquals(2, postbackDispatcher.getDeliveryLogs().size(), "审核通过后应立即补发下游渠道 Postback");
        assertTrue(postbackDispatcher.getDeliveryLogs().get(1).targetUrl().contains("tx_id=tx-med-risk-02"));
    }

    @Test
    @DisplayName("3. 虚拟线程异步非阻塞 Postback 分发模式验证")
    void testAsyncPostbackDispatchWithVirtualThreads() throws InterruptedException {
        postbackService.setAsyncPostbackDispatch(true);

        AffiliatePartner partner = new AffiliatePartner(
                "aff-async", "tenant-1", "Async Media",
                AffiliatePartner.Status.ACTIVE, AffiliatePartner.Tier.STANDARD,
                "https://async-partner.com/pb?click_id={click_id}&payout={payout}",
                AffiliatePartner.PaymentTerm.NET_30, new BigDecimal("50.00"), Instant.now()
        );
        postbackService.registerPartner(partner);

        Offer offer = new Offer(
                "off-async", "tenant-1", "adv-1", "Async Offer",
                "https://offer.com?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("6.00"), new BigDecimal("10.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now()
        );
        offerService.save(offer);

        ClickTrackerService.ClickTrackingResult click = clickTracker.trackClick(
                offer, "aff-async", "sub1", null, null, null, null, "8.8.8.8", "Mozilla/5.0", "US", 1
        );
        Conversion conv = postbackService.processPostback(
                click.clickId(), "tx-async-001", BigDecimal.ZERO, click.session().createdAt().plusSeconds(10)
        );
        assertNotNull(conv);

        // 等待虚拟线程完成投递并回调更新状态
        long deadline = System.currentTimeMillis() + 2000;
        while (postbackDispatcher.getDeliveryLogs().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, postbackDispatcher.getDeliveryLogs().size());
    }

    @Test
    @DisplayName("4. 确定性 conversionTime 归因重算一致性验证")
    void testDeterministicConversionTimeAttribution() {
        ConversionAttributionService attributionService = new ConversionAttributionService(null, null, null, null, null);
        Instant historicalConversionTime = Instant.parse("2026-05-01T12:00:00Z");

        List<TouchPoint> touchPoints = List.of(
                new TouchPoint("tp1", "u1", "s1", TouchPointType.CLICK, "aff_early", "off_1", "c1", "seo", "web", "camp1",
                        historicalConversionTime.minus(5, ChronoUnit.DAYS)),
                new TouchPoint("tp2", "u1", "s2", TouchPointType.CLICK, "aff_recent", "off_1", "c2", "social", "app", "camp1",
                        historicalConversionTime.minus(2, ChronoUnit.HOURS))
        );

        BigDecimal value = new BigDecimal("100.00");
        List<AttributionCredit> run1 = attributionService.dataDrivenAttribution(touchPoints, value, historicalConversionTime);
        List<AttributionCredit> run2 = attributionService.dataDrivenAttribution(touchPoints, value, historicalConversionTime);

        assertEquals(run1.get(0).creditedValue(), run2.get(0).creditedValue());
        assertEquals(run1.get(1).creditedValue(), run2.get(1).creditedValue());
        assertTrue(run1.get(1).creditedValue().compareTo(run1.get(0).creditedValue()) > 0, "距历史转化时刻更近的触点应获得更高归因权重");
    }
}
