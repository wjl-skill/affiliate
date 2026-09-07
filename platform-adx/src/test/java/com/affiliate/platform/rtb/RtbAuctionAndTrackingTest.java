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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RtbAuctionAndTrackingTest {

    private OpenRtbAuctionService auctionService;
    private InMemoryBudgetService budgetService;
    private HmacTokenService hmacService;
    private InMemoryRepository<AdSlot> slotRepo;
    private InMemoryRepository<Creative> creativeRepo;
    private InMemoryRepository<Auction> auctionRepo;

    @BeforeEach
    void setup() {
        slotRepo = new InMemoryRepository<>(AdSlot::id);
        creativeRepo = new InMemoryRepository<>(Creative::id);
        auctionRepo = new InMemoryRepository<>(Auction::id);

        AdSlotService slotService = new AdSlotService(slotRepo);
        CreativeService creativeService = new CreativeService(creativeRepo);
        RuleBasedBidStrategy strategy = new RuleBasedBidStrategy();
        budgetService = new InMemoryBudgetService();
        InMemoryFrequencyCapService freqService = new InMemoryFrequencyCapService();
        hmacService = new HmacTokenService("test-secret-key-for-unit-tests-1234");

        auctionService = new OpenRtbAuctionService(
                slotService,
                creativeService,
                strategy,
                auctionRepo,
                budgetService,
                freqService,
                hmacService
        );

        // Prepopulate an ad slot and a creative
        slotRepo.save(new AdSlot("slot1", "Header Banner", 300, 250, 1.0, true, true, Instant.now()));
        creativeRepo.save(new Creative("cr1", "Promo Creative", CreativeType.BANNER, "https://cdn.example.com/banner.png", "https://example.com", 300, 250, Set.of(), true, Instant.now()));
    }

    @Test
    void testBidWithBudgetAndTokenGeneration() {
        // Sufficient budget
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
        OpenRtb.Bid bid = response.seatbid().get(0).bid().get(0);

        assertTrue(bid.price() > 1.0);
        assertNotNull(bid.nurl());
        assertTrue(bid.nurl().startsWith("/rtb/win?token="));

        // Parse token and verify
        String token = bid.nurl().substring("/rtb/win?token=".length());
        HmacTokenService.TrackingPayload payload = hmacService.verifyAndParse(token);
        assertEquals("public", payload.tenantId());
        assertEquals("campaign_default-advertiser", payload.campaignId());
        assertEquals("WIN", payload.eventType());
    }

    @Test
    void testBidBlockedWhenBudgetExhausted() {
        // Set zero budget
        budgetService.setBudget("public", "campaign_default-advertiser", BigDecimal.ZERO);

        OpenRtb.BidRequest request = new OpenRtb.BidRequest(
                "req2",
                List.of(new OpenRtb.Imp("slot1", new OpenRtb.Banner(300, 250, List.of()), null, 1.0, "USD")),
                null, null, null
        );

        OpenRtb.BidResponse response = auctionService.bid(request);
        assertNotNull(response);
        // Should have no bids
        assertTrue(response.seatbid().isEmpty());
    }

    @Test
    void testTamperedTokenFailsVerification() {
        String token = hmacService.generateToken("auc1", "res1", "tenant1", "camp1", 2.50, "WIN");
        // Tamper with one character
        char lastChar = token.charAt(token.length() - 1);
        char altered = (lastChar == 'a') ? 'b' : 'a';
        String tampered = token.substring(0, token.length() - 1) + altered;

        assertThrows(SecurityException.class, () -> hmacService.verifyAndParse(tampered));
    }
}
