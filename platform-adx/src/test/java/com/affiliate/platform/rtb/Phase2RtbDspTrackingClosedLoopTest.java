package com.affiliate.platform.rtb;

import com.affiliate.platform.budget.BudgetService;
import com.affiliate.platform.budget.InMemoryBudgetService;
import com.affiliate.platform.budget.InMemoryFrequencyCapService;
import com.affiliate.platform.cdp.RealtimeEventTraitEngine;
import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.domain.Auction;
import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.dsp.*;
import com.affiliate.platform.event.InMemoryEventPublisher;
import com.affiliate.platform.event.InMemoryOutboxStore;
import com.affiliate.platform.repository.InMemoryRepository;
import com.affiliate.platform.service.AdSlotService;
import com.affiliate.platform.service.CreativeService;
import com.affiliate.platform.ssp.DynamicYieldManager;
import com.affiliate.platform.web.RtbController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 升级验证：DSP 两阶段倒排索引召回、BidModifier、Loss Notice 闭环与 RTB Click 归因打通测试")
class Phase2RtbDspTrackingClosedLoopTest {

    @Test
    @DisplayName("1. DSP 倒排索引 + AdGroup 两阶段召回与 BidModifier 动态乘数接入 RTB 热路径测试")
    void testTwoStageDspRecallAndBidModifierInRtbHotPath() {
        InMemoryRepository<AdSlot> slotRepo = new InMemoryRepository<>(AdSlot::id);
        InMemoryRepository<Creative> creativeRepo = new InMemoryRepository<>(Creative::id);
        InMemoryRepository<Auction> auctionRepo = new InMemoryRepository<>(Auction::id);

        AdSlotService slotService = new AdSlotService(slotRepo);
        CreativeService creativeService = new CreativeService(creativeRepo);
        RuleBasedBidStrategy strategy = new RuleBasedBidStrategy();
        InMemoryBudgetService budgetService = new InMemoryBudgetService();
        InMemoryFrequencyCapService freqService = new InMemoryFrequencyCapService();
        HmacTokenService hmacService = new HmacTokenService("test-secret-key-for-phase2-rtb-123456");
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());

        // 初始化 DSP 倒排索引与广告组
        CampaignInvertedIndex campaignIndex = new CampaignInvertedIndex();
        InMemoryRepository<Campaign> campaignRepo = new InMemoryRepository<>(Campaign::id);
        CampaignService campaignService = new CampaignService(campaignRepo, eventPublisher, campaignIndex);

        Campaign fintechCamp = new Campaign(
                "camp_fintech_vip", "adv_fintech_88", "Fintech Platinum Card",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30),
                new BigDecimal("5000.00"), new BigDecimal("6.50"),
                Set.of("finance.portal.com"), Set.of(1),
                Campaign.Status.ACTIVE, Instant.now()
        );
        Campaign created = campaignService.create(fintechCamp);
        Campaign activeCamp = campaignService.setActive(created.id(), true);

        AdGroup adGroup = new AdGroup(
                "ag_fintech_us_mob", activeCamp.id(), "US Mobile High Value",
                new BiddingStrategy(BiddingStrategy.BiddingType.FIXED_CPM, new BigDecimal("7.20"), new BigDecimal("15.00"), 1.0),
                Set.of("US"), Set.of(1), Set.of("finance.portal.com"),
                Dayparting.allHours(), Set.of(), Set.of(),
                AdGroup.Status.ACTIVE, Instant.now()
        );
        campaignService.registerAdGroup(adGroup);

        BidModifierEngine bidModifierEngine = new BidModifierEngine();
        bidModifierEngine.setGeoMultiplier("US", new BigDecimal("1.25"));

        DynamicYieldManager yieldManager = new DynamicYieldManager();
        RealtimeEventTraitEngine traitEngine = new RealtimeEventTraitEngine();
        DeepCtrCvrInferenceEngine inferenceEngine = new DeepCtrCvrInferenceEngine();
        DynamicBidShadingEngine bidShadingEngine = new DynamicBidShadingEngine();

        RtbSmartBiddingBridge bridge = new RtbSmartBiddingBridge(
                yieldManager, traitEngine, inferenceEngine, bidShadingEngine
        );
        bridge.setCampaignService(campaignService);
        bridge.setCampaignInvertedIndex(campaignIndex);
        bridge.setBidModifierEngine(bidModifierEngine);

        OpenRtbAuctionService auctionService = new OpenRtbAuctionService(
                slotService, creativeService, strategy, auctionRepo, budgetService, freqService, hmacService
        );
        auctionService.setSmartBiddingBridge(bridge);

        slotRepo.save(new AdSlot("slot_fin", "Finance Banner", 300, 250, 1.20, true, true, Instant.now()));
        creativeRepo.save(new Creative("cr_fin", "Card Banner", CreativeType.BANNER,
                "https://cdn.fin.com/ad.png", "https://fin.com/apply?click_id={click_id}", 300, 250, Set.of(), true, Instant.now()));
        budgetService.setBudget("public", activeCamp.id(), new BigDecimal("1000.00"));

        OpenRtb.BidRequest request = new OpenRtb.BidRequest(
                "req_fin_01",
                List.of(new OpenRtb.Imp("slot_fin", new OpenRtb.Banner(300, 250, List.of()), null, 1.20, "USD")),
                new OpenRtb.Site("finance.portal.com", "https://finance.portal.com/markets", null),
                new OpenRtb.Device("Mozilla/5.0 (iPhone)", "203.0.113.9", 1, "US"),
                new OpenRtb.User("user_fin_01", null)
        );

        OpenRtb.BidResponse response = auctionService.bid(request);
        assertNotNull(response);
        assertEquals(1, response.seatbid().size());

        OpenRtb.Bid bid = response.seatbid().get(0).bid().get(0);
        assertNotNull(bid.nurl());
        assertNotNull(bid.lurl(), "OpenRTB Bid 响应必须包含 Loss Notice lurl");
        assertTrue(bid.lurl().startsWith("/rtb/loss?token="));

        String winToken = bid.nurl().substring("/rtb/win?token=".length());
        HmacTokenService.TrackingPayload winPayload = hmacService.verifyAndParse(winToken);
        assertEquals(activeCamp.id(), winPayload.campaignId(),
                "应通过 DSP 倒排索引真实召回激活 Campaign 而非硬编码 default-advertiser");

        Map<String, Object> bridgeMetrics = bridge.getMetricsSnapshot();
        assertTrue(((Number) bridgeMetrics.get("dspIndexRecalledBids")).longValue() >= 1L);
    }

    @Test
    @DisplayName("2. OpenRTB Loss Notice (/rtb/loss) 预算预占释放与 DSP Bid Shading 在线竞败反馈闭环测试")
    void testLossNoticeReleasesBudgetAndTriggersDspLossFeedback() {
        InMemoryRepository<AdSlot> slotRepo = new InMemoryRepository<>(AdSlot::id);
        InMemoryRepository<Creative> creativeRepo = new InMemoryRepository<>(Creative::id);
        InMemoryRepository<Auction> auctionRepo = new InMemoryRepository<>(Auction::id);

        AdSlotService slotService = new AdSlotService(slotRepo);
        CreativeService creativeService = new CreativeService(creativeRepo);
        InMemoryBudgetService budgetService = new InMemoryBudgetService();
        HmacTokenService hmacService = new HmacTokenService("test-secret-key-for-loss-loop-123456");
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());

        DeepCtrCvrInferenceEngine inferenceEngine = new DeepCtrCvrInferenceEngine();
        DynamicBidShadingEngine bidShadingEngine = new DynamicBidShadingEngine();
        DspOnlineFeedbackSubscriber dspSubscriber = new DspOnlineFeedbackSubscriber(
                inferenceEngine, bidShadingEngine, eventPublisher
        );
        dspSubscriber.initSubscription();

        OpenRtbAuctionService auctionService = new OpenRtbAuctionService(
                slotService, creativeService, new RuleBasedBidStrategy(), auctionRepo,
                budgetService, new InMemoryFrequencyCapService(), hmacService,
                null, new CreativeInvertedIndex()
        );
        RtbController controller = new RtbController(auctionService, hmacService, budgetService, eventPublisher);

        // 模拟竞价阶段为 camp_loss_01 预占了 $4.00 预算（总额 $10.00，剩余 $6.00）
        budgetService.setBudget("public", "camp_loss_01", new BigDecimal("10.00"));
        BudgetService.Reservation res = budgetService.reserve("public", "camp_loss_01", "user_01", new BigDecimal("4.00"));
        assertNotNull(res);
        // 剩余仅 $6.00 时，若尝试再预占 $8.00 会因余额不足抛异常
        assertThrows(IllegalStateException.class,
                () -> budgetService.reserve("public", "camp_loss_01", "user_02", new BigDecimal("8.00")));

        String lossToken = hmacService.generateToken(
                "auc_loss_01", res.id(), "public", "camp_loss_01", 4000.0, "LOSS"
        );

        // 调用 /rtb/loss
        ResponseEntity<Map<String, Object>> lossResp = controller.lossNotice(lossToken, 102);
        assertEquals(200, lossResp.getStatusCode().value());
        assertEquals("LOSS_RECORDED", lossResp.getBody().get("status"));

        // 1. 验证预占预算已全额释放回可用余额 ($10.00)，此时预占 $8.00 成功！
        BudgetService.Reservation afterReleaseRes = budgetService.reserve("public", "camp_loss_01", "user_02", new BigDecimal("8.00"));
        assertNotNull(afterReleaseRes, "竞败通知释放预占预算后，应当可以成功预占 $8.00");

        // 2. 验证 auction.loss.v1 事件已驱动 DspOnlineFeedbackSubscriber 更新 BidShading 竞败水位
        assertEquals(1L, dspSubscriber.getLossFeedbackCount());
    }

    @Test
    @DisplayName("3. /rtb/click 点击追踪会话生成与落地页 {click_id} 宏替换打通测试")
    void testRtbClickSessionAndMacroReplacement() throws Exception {
        InMemoryRepository<AdSlot> slotRepo = new InMemoryRepository<>(AdSlot::id);
        InMemoryRepository<Creative> creativeRepo = new InMemoryRepository<>(Creative::id);
        InMemoryRepository<Auction> auctionRepo = new InMemoryRepository<>(Auction::id);

        HmacTokenService hmacService = new HmacTokenService("test-secret-key-for-click-macro-123456");
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        InMemoryBudgetService budgetService = new InMemoryBudgetService();

        OpenRtbAuctionService auctionService = new OpenRtbAuctionService(
                new AdSlotService(slotRepo), new CreativeService(creativeRepo),
                new RuleBasedBidStrategy(), auctionRepo, budgetService,
                new InMemoryFrequencyCapService(), hmacService,
                null, new CreativeInvertedIndex()
        );
        RtbController controller = new RtbController(auctionService, hmacService, budgetService, eventPublisher);

        String clickToken = hmacService.generateToken(
                "auc_clk_01", "res_clk_01", "public", "camp_ecom_77", 2.00, "CLICK"
        );
        String landingUrlWithMacro = "https://advertiser.shop/landing?click_id={click_id}&camp=camp_ecom_77";

        MockHttpServletResponse mockResp = new MockHttpServletResponse();
        controller.click(clickToken, landingUrlWithMacro, mockResp);
        assertEquals(302, mockResp.getStatus());

        String clickId = mockResp.getHeader("X-RTB-Click-Id");
        assertNotNull(clickId);
        assertTrue(clickId.startsWith("rtb_clk_"));

        String redirectedUrl = mockResp.getRedirectedUrl();
        assertNotNull(redirectedUrl);
        assertTrue(redirectedUrl.contains("click_id=" + clickId), "重定向 URL 必须完成 {click_id} 宏替换");
        assertFalse(redirectedUrl.contains("{click_id}"));

        // 验证 RtbClickSession 已被登记，可供后续转化归因回溯
        Optional<RtbController.RtbClickSession> sessionOpt = controller.findRtbClickSession(clickId);
        assertTrue(sessionOpt.isPresent());
        assertEquals("auc_clk_01", sessionOpt.get().auctionId());
        assertEquals("camp_ecom_77", sessionOpt.get().campaignId());
    }
}
