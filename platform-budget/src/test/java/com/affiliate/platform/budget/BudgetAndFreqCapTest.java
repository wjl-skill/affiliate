package com.affiliate.platform.budget;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BudgetAndFreqCapTest {

    @Test
    void testInMemoryBudgetReserveAndConfirm() {
        InMemoryBudgetService budgetService = new InMemoryBudgetService();
        budgetService.setBudget("t1", "camp1", new BigDecimal("100.00"));

        // Reserve 30
        BudgetService.Reservation res1 = budgetService.reserve("t1", "camp1", "userA", new BigDecimal("30.00"));
        assertNotNull(res1.id());

        // Reserve 60
        BudgetService.Reservation res2 = budgetService.reserve("t1", "camp1", "userB", new BigDecimal("60.00"));
        assertNotNull(res2.id());

        // Attempt reserve 20 (only 10 left) -> should fail
        assertThrows(IllegalStateException.class, () ->
            budgetService.reserve("t1", "camp1", "userC", new BigDecimal("20.00"))
        );

        // Confirm res1 (removes reservation, doesn't restore budget)
        budgetService.confirm(res1);

        // Release res2 (restores 60 to budget)
        budgetService.release(res2);

        // Now reserve 50 should succeed
        BudgetService.Reservation res3 = budgetService.reserve("t1", "camp1", "userD", new BigDecimal("50.00"));
        assertNotNull(res3.id());
    }

    @Test
    void testFrequencyCap() {
        InMemoryFrequencyCapService freqService = new InMemoryFrequencyCapService();
        String tenant = "t1";
        String campaign = "c1";
        String user = "user1";

        // Limit: 2 impressions per 1 minute
        assertTrue(freqService.checkAndIncrement(tenant, campaign, user, 2, Duration.ofMinutes(1)));
        assertTrue(freqService.checkAndIncrement(tenant, campaign, user, 2, Duration.ofMinutes(1)));
        // 3rd should be capped
        assertFalse(freqService.checkAndIncrement(tenant, campaign, user, 2, Duration.ofMinutes(1)));

        // Reset
        freqService.reset(tenant, campaign, user);
        assertTrue(freqService.checkAndIncrement(tenant, campaign, user, 2, Duration.ofMinutes(1)));
    }
}
