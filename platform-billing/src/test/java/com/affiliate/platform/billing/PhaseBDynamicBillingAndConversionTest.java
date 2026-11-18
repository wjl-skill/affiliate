package com.affiliate.platform.billing;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.InMemoryEventPublisher;
import com.affiliate.platform.event.InMemoryOutboxStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase B 验证：动态分成比例配置与网盟转化双分录自动记账测试")
class PhaseBDynamicBillingAndConversionTest {

    @Test
    @DisplayName("1. 验证按租户/广告位动态分成比例计算 Win Charge（替代 80/20 硬编码）")
    void testDynamicPlatformFeeRateOnAuctionWin() {
        InMemoryBillingService billingService = new InMemoryBillingService();
        InMemoryEventPublisher publisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        BillingEventStreamSubscriber subscriber = new BillingEventStreamSubscriber(billingService, publisher);
        subscriber.initSubscription();

        // 为 tenant_vip / slot_premium 配置 15% 平台抽成（媒体占 85%）
        subscriber.setPlatformFeeRate("tenant_vip", "slot_premium", 0.15);

        publisher.publish(DomainEvent.create(
                "auction.win.v1",
                "tenant_vip",
                "auc_dyn_01",
                Map.of("slotId", "slot_premium", "campaignId", "camp_1", "price", 10.00)
        ));

        assertEquals(1L, subscriber.getProcessedWinCount());
        List<BillingService.BillingEntry> entries = billingService.list("tenant_vip");
        assertEquals(3, entries.size());

        BigDecimal charge = entries.stream()
                .filter(e -> e.type() == BillingService.EntryType.ADVERTISER_CHARGE)
                .map(BillingService.BillingEntry::amount)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal pubRev = entries.stream()
                .filter(e -> e.type() == BillingService.EntryType.PUBLISHER_REVENUE)
                .map(BillingService.BillingEntry::amount)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal fee = entries.stream()
                .filter(e -> e.type() == BillingService.EntryType.PLATFORM_FEE)
                .map(BillingService.BillingEntry::amount)
                .findFirst().orElse(BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("0.010000").compareTo(charge));
        assertEquals(0, new BigDecimal("0.008500").compareTo(pubRev));
        assertEquals(0, new BigDecimal("0.001500").compareTo(fee));
        assertEquals(0, charge.compareTo(pubRev.add(fee)), "借贷双方必须严格平账");
    }

    @Test
    @DisplayName("2. 验证 conversion.approved.v1 自动触发广告主扣费 + 渠道佣金 + 平台利润复式记账")
    void testConversionApprovedDoubleEntryAccounting() {
        InMemoryBillingService billingService = new InMemoryBillingService();
        InMemoryEventPublisher publisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        BillingEventStreamSubscriber subscriber = new BillingEventStreamSubscriber(billingService, publisher);
        subscriber.initSubscription();

        publisher.publish(DomainEvent.create(
                "conversion.approved.v1",
                "tenant_aff",
                "conv_1001",
                Map.of("conversionId", "conv_1001", "offerId", "off_saas", "affiliateId", "aff_88",
                        "revenue", 25.00, "payout", 18.00)
        ));

        assertEquals(1L, subscriber.getProcessedConversionCount());
        List<BillingService.BillingEntry> entries = billingService.list("tenant_aff");
        assertEquals(3, entries.size());

        BigDecimal debit = entries.stream()
                .filter(e -> e.direction() == BillingService.EntryDirection.DEBIT)
                .map(BillingService.BillingEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = entries.stream()
                .filter(e -> e.direction() == BillingService.EntryDirection.CREDIT)
                .map(BillingService.BillingEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, new BigDecimal("25.000000").compareTo(debit));
        assertEquals(0, debit.compareTo(credit), "转化记账借贷总金额必须 100% 平衡");
    }
}
