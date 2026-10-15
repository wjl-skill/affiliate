package com.affiliate.platform.web;

import com.affiliate.platform.rtb.PmpDealMatcher;
import com.affiliate.platform.rtb.PmpDealMatcher.DealType;
import com.affiliate.platform.rtb.PmpDealMatcher.RegisteredDeal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PmpDealControllerTest {

    private PmpDealMatcher dealMatcher;
    private PmpDealController controller;

    @BeforeEach
    void setUp() {
        dealMatcher = new PmpDealMatcher();
        controller = new PmpDealController(dealMatcher);
    }

    @Test
    void testDealLifecycle() {
        // 1. Create deal
        PmpDealController.DealDto dto = new PmpDealController.DealDto(
                "deal-vip-001",
                "adv-nike",
                DealType.PREFERRED_DEAL,
                new BigDecimal("5.50"),
                Set.of("seat-bidder-1", "seat-bidder-2"),
                true
        );
        ResponseEntity<RegisteredDeal> createRes = controller.createOrUpdateDeal(dto);
        assertEquals(200, createRes.getStatusCode().value());
        assertEquals("deal-vip-001", createRes.getBody().dealId());

        // 2. Query deal
        ResponseEntity<?> getRes = controller.getDeal("deal-vip-001");
        assertEquals(200, getRes.getStatusCode().value());
        assertTrue(getRes.getBody() instanceof RegisteredDeal);

        // 3. List deals
        ResponseEntity<Collection<RegisteredDeal>> listRes = controller.listDeals();
        assertEquals(200, listRes.getStatusCode().value());
        assertEquals(1, listRes.getBody().size());

        // 4. Test matching - authorized seat above floor
        PmpDealController.MatchTestRequest matchReq1 = new PmpDealController.MatchTestRequest(
                "deal-vip-001", "seat-bidder-1", new BigDecimal("6.00")
        );
        ResponseEntity<PmpDealMatcher.DealMatchResult> matchRes1 = controller.matchDeal(matchReq1);
        assertTrue(matchRes1.getBody().isMatched());
        assertEquals(DealType.PREFERRED_DEAL.getPriorityWeight(), matchRes1.getBody().priority());

        // 5. Test matching - unauthorized seat
        PmpDealController.MatchTestRequest matchReq2 = new PmpDealController.MatchTestRequest(
                "deal-vip-001", "unauthorized-seat", new BigDecimal("10.00")
        );
        ResponseEntity<PmpDealMatcher.DealMatchResult> matchRes2 = controller.matchDeal(matchReq2);
        assertFalse(matchRes2.getBody().isMatched());
        assertTrue(matchRes2.getBody().message().contains("not authorized"));

        // 6. Test matching - below floor
        PmpDealController.MatchTestRequest matchReq3 = new PmpDealController.MatchTestRequest(
                "deal-vip-001", "seat-bidder-2", new BigDecimal("3.00")
        );
        ResponseEntity<PmpDealMatcher.DealMatchResult> matchRes3 = controller.matchDeal(matchReq3);
        assertFalse(matchRes3.getBody().isMatched());
        assertTrue(matchRes3.getBody().message().contains("below deal floor"));

        // 7. Delete deal
        ResponseEntity<Map<String, Object>> delRes = controller.deleteDeal("deal-vip-001");
        assertEquals(200, delRes.getStatusCode().value());
        assertTrue((Boolean) delRes.getBody().get("success"));

        // 8. Verify deleted
        ResponseEntity<?> getDeleted = controller.getDeal("deal-vip-001");
        assertEquals(404, getDeleted.getStatusCode().value());
    }
}
