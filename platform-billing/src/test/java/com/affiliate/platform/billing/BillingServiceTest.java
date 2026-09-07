package com.affiliate.platform.billing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillingServiceTest {

    @Test
    void testDoubleEntryAndIdempotency() {
        InMemoryBillingService service = new InMemoryBillingService();
        String tenantId = "tenant_test";
        String auctionId = "auc_100";

        // Record debit entry
        BillingService.BillingEntry debit = new BillingService.BillingEntry(
                null,
                tenantId,
                "advertiser_1",
                auctionId,
                BillingService.EntryType.ADVERTISER_CHARGE,
                BillingService.EntryDirection.DEBIT,
                new BigDecimal("5.00"),
                "USD",
                "idem_key_debit_1",
                "Impression charge",
                Instant.now()
        );
        BillingService.BillingEntry savedDebit = service.record(debit);
        assertNotNull(savedDebit.id());
        assertEquals(BillingService.EntryDirection.DEBIT, savedDebit.direction());

        // Attempt to record with duplicate idempotency key -> returns original entry
        BillingService.BillingEntry duplicate = service.record(debit);
        assertEquals(savedDebit.id(), duplicate.id());

        // Record credit entry
        BillingService.BillingEntry credit = new BillingService.BillingEntry(
                null,
                tenantId,
                "publisher_1",
                auctionId,
                BillingService.EntryType.PUBLISHER_REVENUE,
                BillingService.EntryDirection.CREDIT,
                new BigDecimal("4.00"),
                "USD",
                "idem_key_credit_1",
                "Publisher payout",
                Instant.now()
        );
        BillingService.BillingEntry savedCredit = service.record(credit);
        assertNotNull(savedCredit.id());

        List<BillingService.BillingEntry> list = service.list(tenantId);
        assertEquals(2, list.size());
    }
}
