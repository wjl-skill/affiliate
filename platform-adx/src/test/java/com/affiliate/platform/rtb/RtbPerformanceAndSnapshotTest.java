package com.affiliate.platform.rtb;

import com.affiliate.platform.budget.InMemoryBudgetService;
import com.affiliate.platform.budget.InMemoryFrequencyCapService;
import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.domain.Auction;
import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.repository.InMemoryRepository;
import com.affiliate.platform.service.AdSlotService;
import com.affiliate.platform.service.CreativeService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 实时竞价与倒排索引快照性能与度量指标测试
 */
class RtbPerformanceAndSnapshotTest {

    private CreativeInvertedIndex index;
    private SimpleMeterRegistry meterRegistry;
    private RtbMetrics rtbMetrics;
    private InMemoryBudgetService budgetService;
    private InMemoryFrequencyCapService freqService;
    private HmacTokenService hmacService;
    private InMemoryRepository<AdSlot> slotRepo;
    private InMemoryRepository<Creative> creativeRepo;
    private InMemoryRepository<Auction> auctionRepo;
    private OpenRtbAuctionService auctionService;

    @BeforeEach
    void setUp() {
        index = new CreativeInvertedIndex();
        meterRegistry = new SimpleMeterRegistry();
        rtbMetrics = new RtbMetrics(meterRegistry);

        slotRepo = new InMemoryRepository<>(AdSlot::id);
        creativeRepo = new InMemoryRepository<>(Creative::id);
        auctionRepo = new InMemoryRepository<>(Auction::id);

        AdSlotService slotService = new AdSlotService(slotRepo);
        CreativeService creativeService = new CreativeService(creativeRepo);
        RuleBasedBidStrategy strategy = new RuleBasedBidStrategy();
        budgetService = new InMemoryBudgetService();
        freqService = new InMemoryFrequencyCapService();
        hmacService = new HmacTokenService("test-secret-key-for-rtb-unit-tests-1234");

        auctionService = new OpenRtbAuctionService(
                slotService, creativeService, strategy, auctionRepo,
                budgetService, freqService, hmacService,
                new com.affiliate.platform.budget.LocalBudgetSliceService(budgetService),
                index, rtbMetrics
        );

        slotRepo.save(new AdSlot("slot1", "Header Banner", 300, 250, 1.0, true, true, Instant.now()));
    }

    @Test
    @DisplayName("测试 CreativeInvertedIndex 原子不可变快照构建与纳秒级召回")
    void testInvertedIndexSnapshotOperations() {
        Creative cr1 = new Creative("cr_1", "Creative 1", CreativeType.BANNER, "https://cdn.com/1.png", "https://site.com/1", 300, 250, Set.of(), true, Instant.now());
        Creative cr2 = new Creative("cr_2", "Creative 2", CreativeType.BANNER, "https://cdn.com/2.png", "https://site.com/2", 300, 250, Set.of(), true, Instant.now());
        Creative cr3 = new Creative("cr_3", "Creative 3", CreativeType.BANNER, "https://cdn.com/3.png", "https://site.com/3", 728, 90, Set.of(), true, Instant.now());
        Creative crInactive = new Creative("cr_4", "Creative 4", CreativeType.BANNER, "https://cdn.com/4.png", "https://site.com/4", 300, 250, Set.of(), false, Instant.now());

        // 1. 批量加载测试
        index.loadAll(List.of(cr1, cr2, cr3, crInactive));
        assertEquals(3, index.totalActiveCount());

        // 2. 尺寸召回
        List<Creative> c300x250 = index.findCandidates(300, 250);
        assertEquals(2, c300x250.size());

        List<Creative> c728x90 = index.findCandidates(728, 90);
        assertEquals(1, c728x90.size());
        assertEquals("cr_3", c728x90.get(0).id());

        // 3. 不匹配尺寸
        List<Creative> cEmpty = index.findCandidates(160, 600);
        assertTrue(cEmpty.isEmpty());

        // 4. 动态下线移除
        index.remove("cr_1");
        assertEquals(2, index.totalActiveCount());
        assertEquals(1, index.findCandidates(300, 250).size());
    }

    @Test
    @DisplayName("测试 OpenRTB 竞价链路全景度量与出价成功统计")
    void testRtbMetricsOnWinningBid() {
        Creative cr = new Creative("cr1", "Promo Creative", CreativeType.BANNER, "https://cdn.com/b.png", "https://site.com", 300, 250, Set.of(), true, Instant.now());
        index.index(cr);
        creativeRepo.save(cr);

        budgetService.setBudget("public", "campaign_default-advertiser", new BigDecimal("100.00"));

        OpenRtb.BidRequest request = new OpenRtb.BidRequest(
                "req1",
                List.of(new OpenRtb.Imp("slot1", new OpenRtb.Banner(300, 250, List.of()), null, 1.0, "USD")),
                new OpenRtb.Site("example.com", "/news", null),
                new OpenRtb.Device("Mozilla/5.0", "1.2.3.4", 1, "US"),
                new OpenRtb.User("user123", null)
        );

        OpenRtb.BidResponse response = auctionService.bid(request);

        assertNotNull(response);
        assertEquals(1, response.seatbid().size());
        assertEquals(1, response.seatbid().get(0).bid().size());

        // 验证 RtbMetrics 计数
        assertEquals(1.0, meterRegistry.get("rtb.bid.requests.total").tag("decision", "bid").counter().count());
    }

    @Test
    @DisplayName("测试预算耗尽时的放弃出价与指标计数")
    void testRtbMetricsBudgetExhausted() {
        Creative cr = new Creative("cr1", "Promo Creative", CreativeType.BANNER, "https://cdn.com/b.png", "https://site.com", 300, 250, Set.of(), true, Instant.now());
        index.index(cr);
        creativeRepo.save(cr);

        // 设置 0 预算模拟耗尽
        budgetService.setBudget("public", "campaign_default-advertiser", BigDecimal.ZERO);

        OpenRtb.BidRequest request = new OpenRtb.BidRequest(
                "req2",
                List.of(new OpenRtb.Imp("slot1", new OpenRtb.Banner(300, 250, List.of()), null, 1.0, "USD")),
                null, null, null
        );

        OpenRtb.BidResponse response = auctionService.bid(request);

        assertNotNull(response);
        assertTrue(response.seatbid().isEmpty());

        assertEquals(1.0, meterRegistry.get("rtb.budget.exhausted.total").counter().count());
        assertEquals(1.0, meterRegistry.get("rtb.bid.requests.total").tag("decision", "nobid").counter().count());
    }
}
