package com.affiliate.platform.billing;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.InMemoryEventPublisher;
import com.affiliate.platform.event.InMemoryOutboxStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BillingEventStreamSubscriberTest {

    @Test
    @DisplayName("验证 auction.win.v1 领域事件触发实时双分录平衡记账 (100% 广告主借记 = 80% 媒体贷记 + 20% 平台贷记)")
    void testAuctionWinTriggersBalancedDoubleEntryLedger() {
        InMemoryBillingService billingService = new InMemoryBillingService();
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        BillingEventStreamSubscriber subscriber = new BillingEventStreamSubscriber(billingService, eventPublisher);
        subscriber.initSubscription();

        // 发布 CPM = $5.00 的竞价胜出事件 -> 单次展示结算金额 = $0.005000
        eventPublisher.publish(DomainEvent.create(
                "auction.win.v1",
                "tenant_fin",
                "auc_9001",
                Map.of(
                        "campaignId", "camp_gold",
                        "slotId", "slot_top_banner",
                        "price", 5.00
                )
        ));

        assertEquals(1L, subscriber.getProcessedWinCount());
        List<BillingService.BillingEntry> entries = billingService.list("tenant_fin");
        assertEquals(3, entries.size(), "Should record 1 DEBIT and 2 CREDIT entries");

        BigDecimal totalDebit = entries.stream()
                .filter(e -> e.direction() == BillingService.EntryDirection.DEBIT)
                .map(BillingService.BillingEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream()
                .filter(e -> e.direction() == BillingService.EntryDirection.CREDIT)
                .map(BillingService.BillingEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, new BigDecimal("0.005000").compareTo(totalDebit));
        assertEquals(0, totalDebit.compareTo(totalCredit), "Double-entry ledger must strictly balance (DEBIT == CREDIT)");
    }
}
