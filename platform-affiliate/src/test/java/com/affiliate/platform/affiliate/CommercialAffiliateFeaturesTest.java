package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.OfferGoal;
import com.affiliate.platform.affiliate.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommercialAffiliateFeaturesTest {

    private OfferService offerService;
    private ProbabilisticAttributionEngine probabilisticEngine;
    private ClickTrackerService clickTracker;
    private AffiliateAntiFraudEngine antiFraudEngine;
    private PublisherPostbackDispatcher postbackDispatcher;
    private S2sPostbackService postbackService;

    @BeforeEach
    void setUp() {
        offerService = new OfferService();
        probabilisticEngine = new ProbabilisticAttributionEngine();
        clickTracker = new ClickTrackerService(null, null, probabilisticEngine);
        antiFraudEngine = new AffiliateAntiFraudEngine();
        postbackDispatcher = new PublisherPostbackDispatcher();
        postbackService = new S2sPostbackService(clickTracker, offerService, antiFraudEngine, postbackDispatcher, null, null, probabilisticEngine);

        // 创建基准 Offer
        Offer offer = new Offer("off-multi-1", "tenant-1", "adv-1", "FinTech Global", "https://fintech.com?click_id={click_id}",
                Offer.PayoutType.CPA, new BigDecimal("5.00"), new BigDecimal("10.00"), Offer.Status.ACTIVE,
                1000, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        offerService.save(offer);

        // 注册渠道
        AffiliatePartner partner = new AffiliatePartner("aff-100", "tenant-1", "Apex Media",
                AffiliatePartner.Status.ACTIVE, AffiliatePartner.Tier.VIP, "https://apex.com/pb?click_id={click_id}",
                AffiliatePartner.PaymentTerm.NET_15, new BigDecimal("100.00"), Instant.now());
        postbackService.registerPartner(partner);
    }

    @Test
    void multiEventFunnelGoalsAttribution() {
        // 配置 3 个深度事件目标
        OfferGoal regGoal = new OfferGoal("goal-reg", "off-multi-1", "User Register", OfferGoal.EventType.REGISTRATION,
                new BigDecimal("8.00"), new BigDecimal("15.00"), false, true, 30);
        OfferGoal depositGoal = new OfferGoal("goal-dep", "off-multi-1", "First Deposit", OfferGoal.EventType.FIRST_DEPOSIT,
                new BigDecimal("50.00"), new BigDecimal("80.00"), false, true, 30);

        offerService.addGoal(regGoal);
        offerService.addGoal(depositGoal);

        // 模拟点击
        Offer offer = offerService.find("off-multi-1").orElseThrow();
        ClickTrackerService.ClickTrackingResult click = clickTracker.trackClick(offer, "aff-100",
                "campaignA", "ad1", "banner", null, null,
                "192.168.1.50", "Mozilla/5.0 Chrome/120.0", "US", 1);

        Instant conversionTime = click.session().createdAt().plusSeconds(60); // CTIT = 60s (正常范围)

        // 1. 触发 REGISTRATION 事件
        Conversion regConv = postbackService.processPostback(click.clickId(), "tx-reg-1", BigDecimal.ZERO, "goal-reg", "off-multi-1",
                "192.168.1.50", "Mozilla/5.0 Chrome/120.0", 1, conversionTime);

        assertEquals(0, new BigDecimal("8.00").compareTo(regConv.payout()));
        assertEquals(0, new BigDecimal("15.00").compareTo(regConv.revenue()));
        assertEquals(Conversion.Status.PENDING, regConv.status());

        // 2. 触发 FIRST_DEPOSIT 事件 (同一 click_id, 不同 txId 与 goalId)
        Conversion depConv = postbackService.processPostback(click.clickId(), "tx-dep-1", BigDecimal.ZERO, "goal-dep", "off-multi-1",
                "192.168.1.50", "Mozilla/5.0 Chrome/120.0", 1, conversionTime.plusSeconds(300));

        assertEquals(0, new BigDecimal("50.00").compareTo(depConv.payout()));
        assertEquals(0, new BigDecimal("80.00").compareTo(depConv.revenue()));
        assertEquals(Conversion.Status.PENDING, depConv.status());
    }

    @Test
    void probabilisticFingerprintFallbackWhenClickIdMissing() {
        Offer offer = offerService.find("off-multi-1").orElseThrow();

        // 1. 产生点击，自动向指纹池注册设备特征 (IP: 203.0.113.88)
        ClickTrackerService.ClickTrackingResult click = clickTracker.trackClick(offer, "aff-100",
                "sub-prob-1", null, null, null, null,
                "203.0.113.88", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148", "US", 1);

        assertNotNull(click.clickId());

        // 2. 模拟广告主 S2S Postback 丢失了 click_id (null)，但传递了相同的 IP 与 UA 环境
        Instant convTime = click.session().createdAt().plusSeconds(120);
        Conversion conv = postbackService.processPostback(
                null, // click_id 丢失
                "tx-prob-999",
                BigDecimal.ZERO,
                null,
                "off-multi-1",
                "203.0.113.88", // 相同 IP
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
                1,
                convTime
        );

        // 成功通过指纹概率兜底归因到原始点击
        assertEquals(click.clickId(), conv.clickId());
        assertEquals("aff-100", conv.affiliateId());
        assertEquals(0, new BigDecimal("5.00").compareTo(conv.payout()));
        assertEquals(Conversion.Status.PENDING, conv.status());
    }

    @Test
    void antiFraudMultiFactorScoringAndBlacklist() {
        Offer offer = offerService.find("off-multi-1").orElseThrow();

        // 场景 A: 点击注入 (CTIT < 3s)
        ClickTrackerService.ClickTrackingResult click1 = clickTracker.trackClick(offer, "aff-100",
                null, null, null, null, null, "192.168.1.10", "Mozilla/5.0 Chrome", "US", 1);

        Conversion convInjection = postbackService.processPostback(
                click1.clickId(), "tx-fraud-1", BigDecimal.ZERO, click1.session().createdAt().plusSeconds(1)
        );
        assertEquals(Conversion.Status.FRAUD_SUSPECTED, convInjection.status());
        assertTrue(convInjection.rejectionReason().contains("FAST_CONVERSION_CTIT_UNDER_3S"));

        // 场景 B: 数据中心机房 IP 命中
        ClickTrackerService.ClickTrackingResult click2 = clickTracker.trackClick(offer, "aff-100",
                null, null, null, null, null, "54.210.10.22", "Mozilla/5.0 Chrome", "US", 1); // AWS IP

        Conversion convDatacenter = postbackService.processPostback(
                click2.clickId(), "tx-fraud-2", BigDecimal.ZERO, click2.session().createdAt().plusSeconds(30)
        );
        // 数据中心机房 IP 被打分记录
        assertNotNull(convDatacenter);
        assertFalse(antiFraudEngine.getRecentRiskLogs().isEmpty());

        // 场景 C: 黑名单一键封禁
        antiFraudEngine.addIpToBlacklist("198.51.100.99");
        ClickTrackerService.ClickTrackingResult click3 = clickTracker.trackClick(offer, "aff-100",
                null, null, null, null, null, "198.51.100.99", "Mozilla/5.0 Chrome", "US", 1);

        Conversion convBlacklisted = postbackService.processPostback(
                click3.clickId(), "tx-fraud-3", BigDecimal.ZERO, click3.session().createdAt().plusSeconds(30)
        );
        assertEquals(Conversion.Status.FRAUD_SUSPECTED, convBlacklisted.status());
        assertTrue(convBlacklisted.rejectionReason().contains("IP_IN_BLACKLIST"));
    }
}
