package com.affiliate.platform.rtb;

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
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@DisplayName("Phase A 验证：RTB 热路径 PID Pacing 预算节流与 rtb_clk_* Redis 会话双写测试")
class PhaseAPacingAndRtbClickBridgeTest {

    @Test
    @DisplayName("1. RTB 热路径接入 BudgetPacingController：超支拦截与消耗累计记录验证")
    void testRtbHotPathPidPacingThrottlingAndSpendTracking() {
        InMemoryRepository<AdSlot> slotRepo = new InMemoryRepository<>(AdSlot::id);
        InMemoryRepository<Creative> creativeRepo = new InMemoryRepository<>(Creative::id);
        InMemoryRepository<Auction> auctionRepo = new InMemoryRepository<>(Auction::id);

        InMemoryBudgetService budgetService = new InMemoryBudgetService();
        HmacTokenService hmacService = new HmacTokenService("test-secret-key-for-phase-a-pacing-1234");
        RtbMetrics rtbMetrics = new RtbMetrics();

        CampaignInvertedIndex campaignIndex = new CampaignInvertedIndex();
        InMemoryRepository<Campaign> campaignRepo = new InMemoryRepository<>(Campaign::id);
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        CampaignService campaignService = new CampaignService(campaignRepo, eventPublisher, campaignIndex);

        Campaign camp = campaignService.create(new Campaign(
                "camp_pacing", "adv_pacing", "Pacing Controlled Campaign",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                new BigDecimal("10.00"), new BigDecimal("8.00"),
                Set.of("news.site.com"), Set.of(1),
                Campaign.Status.ACTIVE, Instant.now()
        ));
        Campaign activeCamp = campaignService.setActive(camp.id(), true);

        RtbSmartBiddingBridge bridge = new RtbSmartBiddingBridge(
                new DynamicYieldManager(), new RealtimeEventTraitEngine(),
                new DeepCtrCvrInferenceEngine(), new DynamicBidShadingEngine()
        );
        bridge.setCampaignService(campaignService);
        bridge.setCampaignInvertedIndex(campaignIndex);
        bridge.setBudgetPacingController(new BudgetPacingController());
        bridge.setPacingElapsedFractionOverride(0.50);

        OpenRtbAuctionService auctionService = new OpenRtbAuctionService(
                new AdSlotService(slotRepo), new CreativeService(creativeRepo),
                new RuleBasedBidStrategy(), auctionRepo, budgetService,
                new InMemoryFrequencyCapService(), hmacService,
                new com.affiliate.platform.budget.LocalBudgetSliceService(budgetService),
                new CreativeInvertedIndex(), rtbMetrics
        );
        auctionService.setSmartBiddingBridge(bridge);

        slotRepo.save(new AdSlot("slot_news", "News Banner", 300, 250, 1.00, true, true, Instant.now()));
        creativeRepo.save(new Creative("cr_news", "News Ad", CreativeType.BANNER,
                "https://cdn.news.com/a.png", "https://news.com", 300, 250, Set.of(), true, Instant.now()));
        budgetService.setBudget("public", activeCamp.id(), new BigDecimal("100.00"));

        OpenRtb.BidRequest req = new OpenRtb.BidRequest(
                "req_p_1",
                List.of(new OpenRtb.Imp("slot_news", new OpenRtb.Banner(300, 250, List.of()), null, 1.00, "USD")),
                new OpenRtb.Site("news.site.com", "https://news.site.com", null),
                new OpenRtb.Device("Mozilla/5.0", "1.1.1.1", 1, "US"),
                new OpenRtb.User("u_pacing_1", null)
        );

        // 1) 正常余额下应顺利出价并累计日内消耗
        OpenRtb.BidResponse okResp = auctionService.bid(req);
        assertEquals(1, okResp.seatbid().size());

        // 2) 模拟日内累计消耗已达日预算 ($10.00) -> Pacing 判定 budgetExhausted=true 立即拦截
        bridge.recordCampaignSpend(activeCamp.id(), 10.00);
        OpenRtb.BidResponse throttledResp = auctionService.bid(req);
        assertTrue(throttledResp.seatbid().isEmpty(), "当 PID Pacing 判定日内预算耗尽时，RTB 热路径应直接拦截出价");
        assertTrue(((Number) bridge.getMetricsSnapshot().get("pacingThrottledBids")).longValue() >= 1L);

        // 3) 若竞败通知释放预占 ($5.00, 即 CPM=5000.0)，应同步回退日内已消耗，活动恢复可竞价状态
        RtbController controller = new RtbController(auctionService, hmacService, budgetService, eventPublisher);
        String lossToken = hmacService.generateToken("auc_loss_p", "res_loss_p", "public", activeCamp.id(), 5000.0, "LOSS");
        controller.lossNotice(lossToken, 102);

        OpenRtb.BidResponse recoveredResp = auctionService.bid(req);
        assertEquals(1, recoveredResp.seatbid().size(), "竞败回退消耗后，活动应恢复正常出价");
    }

    @Test
    @DisplayName("2. RtbController.click 将 rtb_clk_* 会话双写至 Redis 供跨节点 S2S 归因")
    @SuppressWarnings("unchecked")
    void testRtbControllerClickPersistsSessionToRedis() throws Exception {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        Map<String, String> redisStore = new ConcurrentHashMap<>();
        when(mockRedis.opsForValue()).thenReturn(mockOps);
        doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockOps).set(anyString(), anyString(), any(Duration.class));

        HmacTokenService hmacService = new HmacTokenService("test-secret-key-for-rtb-redis-123456");
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        InMemoryBudgetService budgetService = new InMemoryBudgetService();

        OpenRtbAuctionService auctionService = new OpenRtbAuctionService(
                new AdSlotService(new InMemoryRepository<>(AdSlot::id)),
                new CreativeService(new InMemoryRepository<>(Creative::id)),
                new RuleBasedBidStrategy(),
                new InMemoryRepository<>(Auction::id),
                budgetService,
                new InMemoryFrequencyCapService(),
                hmacService
        );
        RtbController controller = new RtbController(auctionService, hmacService, budgetService, eventPublisher);
        controller.setRedisTemplate(mockRedis);

        String token = hmacService.generateToken("auc_r_1", "res_r_1", "tenant_a", "camp_rtb_99", 3.50, "CLICK");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.click(token, "https://offer.example.com/buy?click_id={click_id}", resp);

        String clickId = resp.getHeader("X-RTB-Click-Id");
        assertNotNull(clickId);
        assertTrue(clickId.startsWith("rtb_clk_"));

        String stored = redisStore.get("rtb:click:sess:" + clickId);
        assertNotNull(stored, "rtb_clk_* 会话必须同步写入 Redis");
        assertTrue(stored.contains("camp_rtb_99"));
        assertTrue(stored.contains("tenant_a"));
    }
}
