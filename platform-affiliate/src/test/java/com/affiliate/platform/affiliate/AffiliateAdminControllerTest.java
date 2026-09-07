package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.SmartLink;
import com.affiliate.platform.affiliate.service.*;
import com.affiliate.platform.affiliate.web.AffiliateAdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateAdminControllerTest {

    private AffiliateAdminController controller;
    private OfferService offerService;
    private S2sPostbackService postbackService;
    private ClickTrackerService clickTracker;
    private AffiliateAntiFraudEngine antiFraudEngine;
    private PublisherPostbackDispatcher postbackDispatcher;
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

        controller = new AffiliateAdminController(offerService, postbackService, settlementService, analyticsService, tdsRouter);
    }

    @Test
    void adminOverviewAndOfferCrud() {
        // 创建 Offer
        Offer offer = new Offer("off-admin-1", "tenant-1", "adv-1", "FinTech CPA", "https://bank.com?click_id={click_id}",
                Offer.PayoutType.CPA, new BigDecimal("10.00"), new BigDecimal("15.00"), Offer.Status.ACTIVE,
                100, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        controller.createOffer(offer);

        List<Offer> offers = controller.listOffers();
        assertEquals(1, offers.size());

        ResponseEntity<Offer> fetched = controller.getOffer("off-admin-1");
        assertTrue(fetched.getStatusCode().is2xxSuccessful());
        assertNotNull(fetched.getBody());
        assertEquals("FinTech CPA", fetched.getBody().title());

        // 注册 Partner
        AffiliatePartner partner = new AffiliatePartner("aff-vip-1", "tenant-1", "Apex Media",
                AffiliatePartner.Status.ACTIVE, AffiliatePartner.Tier.VIP, "https://apex.com/pb?click_id={click_id}",
                AffiliatePartner.PaymentTerm.NET_15, new BigDecimal("100.00"), Instant.now());
        controller.savePartner(partner);

        List<AffiliatePartner> partners = controller.listPartners();
        assertEquals(1, partners.size());

        // 测试 Dashboard Overview
        Map<String, Object> overview = controller.getOverview();
        assertEquals(1, overview.get("totalOffers"));
        assertEquals(1L, overview.get("activeOffers"));
        assertEquals(1, overview.get("totalPartners"));
    }

    @Test
    void adminSmartLinkSimulation() {
        Offer offer = new Offer("off-sim", "tenant-1", "adv-1", "Sim Offer", "https://sim.com?click_id={click_id}",
                Offer.PayoutType.CPA, new BigDecimal("5.00"), new BigDecimal("8.00"), Offer.Status.ACTIVE,
                0, null, null, Set.of("US"), Set.of(1), null, Instant.now());
        controller.createOffer(offer);

        SmartLink link = new SmartLink("smart-1", "tenant-1", "US Mobile SmartLink", "Utility",
                List.of("off-sim"), SmartLink.RoutingStrategy.HIGHEST_EPC, null, Instant.now());
        controller.saveSmartLink(link);

        ResponseEntity<Offer> routed = controller.simulateRouting("smart-1", "US", 1);
        assertTrue(routed.getStatusCode().is2xxSuccessful());
        assertNotNull(routed.getBody());
        assertEquals("off-sim", routed.getBody().id());
    }
}
