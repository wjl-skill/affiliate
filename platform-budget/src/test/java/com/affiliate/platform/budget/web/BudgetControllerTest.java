package com.affiliate.platform.budget.web;

import com.affiliate.platform.budget.BudgetAlertService;
import com.affiliate.platform.budget.BudgetService;
import com.affiliate.platform.budget.InMemoryBudgetService;
import com.affiliate.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BudgetControllerTest {

    private InMemoryBudgetService budgetService;
    private BudgetAlertService alertService;
    private BudgetController controller;

    @BeforeEach
    void setUp() {
        budgetService = new InMemoryBudgetService();
        alertService = mock(BudgetAlertService.class);
        controller = new BudgetController(budgetService, null, alertService);
        TenantContext.set("tenant_test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSetAndReserveBudgetFlow() {
        // 1. Set budget 500
        BudgetController.SetBudgetRequest setReq = new BudgetController.SetBudgetRequest(new BigDecimal("500.00"), new BigDecimal("100.00"));
        ResponseEntity<?> setRes = controller.setCampaignBudget("camp_101", setReq);
        assertEquals(200, setRes.getStatusCode().value());

        // 2. Reserve 150
        BudgetController.ReserveRequest reserveReq = new BudgetController.ReserveRequest("camp_101", "user_1", new BigDecimal("150.00"));
        ResponseEntity<?> reserveRes = controller.reserve(reserveReq);
        assertEquals(200, reserveRes.getStatusCode().value());
        assertTrue(reserveRes.getBody() instanceof BudgetService.Reservation);
        BudgetService.Reservation reservation = (BudgetService.Reservation) reserveRes.getBody();
        assertEquals("camp_101", reservation.campaignId());
        assertEquals("tenant_test", reservation.tenantId());
        assertEquals(new BigDecimal("150.00"), reservation.amount());

        // 3. Confirm 150
        BudgetController.ConfirmOrReleaseRequest confirmReq = new BudgetController.ConfirmOrReleaseRequest(
                reservation.id(), "camp_101", "user_1", new BigDecimal("150.00")
        );
        ResponseEntity<?> confirmRes = controller.confirm(confirmReq);
        assertEquals(200, confirmRes.getStatusCode().value());
        assertTrue(((Map<?, ?>) confirmRes.getBody()).containsKey("success"));

        // 4. Reserve another 350
        BudgetController.ReserveRequest reserveReq2 = new BudgetController.ReserveRequest("camp_101", "user_2", new BigDecimal("350.00"));
        ResponseEntity<?> reserveRes2 = controller.reserve(reserveReq2);
        BudgetService.Reservation reservation2 = (BudgetService.Reservation) reserveRes2.getBody();

        // 5. Release 350 (budget returned)
        BudgetController.ConfirmOrReleaseRequest releaseReq = new BudgetController.ConfirmOrReleaseRequest(
                reservation2.id(), "camp_101", "user_2", new BigDecimal("350.00")
        );
        ResponseEntity<?> releaseRes = controller.release(releaseReq);
        assertEquals(200, releaseRes.getStatusCode().value());

        // 6. Credit 200
        BudgetController.CreditBudgetRequest creditReq = new BudgetController.CreditBudgetRequest(new BigDecimal("200.00"));
        ResponseEntity<?> creditRes = controller.creditBudget("camp_101", creditReq);
        assertEquals(200, creditRes.getStatusCode().value());
    }

    @Test
    void testGetCampaignBudgetFallback() {
        ResponseEntity<?> res = controller.getCampaignBudget("camp_missing");
        assertEquals(200, res.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) res.getBody();
        assertEquals("NOT_CONFIGURED", body.get("status"));
        assertEquals("camp_missing", body.get("campaignId"));
    }

    @Test
    void testCheckAlert() {
        ResponseEntity<?> res = controller.checkAlert("camp_alert", new BigDecimal("850.00"), new BigDecimal("1000.00"));
        assertEquals(200, res.getStatusCode().value());
        verify(alertService, times(1)).checkAndAlert("tenant_test", "camp_alert", new BigDecimal("850.00"), new BigDecimal("1000.00"));
    }
}
