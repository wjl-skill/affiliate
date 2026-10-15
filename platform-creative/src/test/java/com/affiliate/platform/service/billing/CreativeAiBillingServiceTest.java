package com.affiliate.platform.service.billing;

import com.affiliate.platform.service.billing.impl.CreativeAiBillingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreativeAiBillingService Tests - Two-Phase Settlement")
class CreativeAiBillingServiceTest {

    private CreativeAiBillingServiceImpl billingService;

    @BeforeEach
    void setUp() {
        billingService = new CreativeAiBillingServiceImpl();
    }

    @Test
    @DisplayName("Should successfully execute reserve, confirm and release lifecycle")
    void testBillingLifecycle() {
        String taskNo = "tsk_test_bill_001";
        BigDecimal estimated = BigDecimal.valueOf(0.75);

        // 1. Reserve
        boolean reserved = billingService.reserveComputeFund("tenant_a", taskNo, estimated);
        assertTrue(reserved);
        assertTrue(billingService.hasActiveHold(taskNo));

        // 2. Confirm
        BigDecimal actual = BigDecimal.valueOf(0.75);
        boolean confirmed = billingService.confirmComputeDeduction(
                "tenant_a", taskNo, actual, "dept_marketing", "prj_001", "ark_seedance_2_0"
        );
        assertTrue(confirmed);
        assertFalse(billingService.hasActiveHold(taskNo));

        // 3. Test failed scenario with release
        String taskNo2 = "tsk_test_bill_002";
        billingService.reserveComputeFund("tenant_a", taskNo2, estimated);
        assertTrue(billingService.hasActiveHold(taskNo2));

        boolean released = billingService.releaseComputeHold("tenant_a", taskNo2);
        assertTrue(released);
        assertFalse(billingService.hasActiveHold(taskNo2));
    }
}
