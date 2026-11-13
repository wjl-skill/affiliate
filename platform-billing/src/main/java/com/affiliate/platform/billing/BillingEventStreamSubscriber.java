package com.affiliate.platform.billing;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 金融双分录账本实时事件流订阅器 (Real-Time Double-Entry Billing Event Subscriber)
 * <p>
 * 监听 {@code auction.win.v1} 领域事件，自动生成严格平衡的复式记账分录：
 * <ul>
 *     <li>借方 (DEBIT)：广告主出资扣费 {@code ADVERTISER_CHARGE} (100% 成交价)；</li>
 *     <li>贷方 (CREDIT)：媒体发布商分成 {@code PUBLISHER_REVENUE} (80%) + 平台技术服务费 {@code PLATFORM_FEE} (20%)；</li>
 *     <li>幂等防重：采用 {@code win_debit:<auctionId>} 幂等键，防止网络重试或重复消费导致双倍扣款。</li>
 * </ul>
 */
@Component
public class BillingEventStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(BillingEventStreamSubscriber.class);
    private static final BigDecimal PUBLISHER_SHARE_RATIO = new BigDecimal("0.80");

    private final BillingService billingService;
    private final EventPublisher eventPublisher;
    private final AtomicLong processedWinCount = new AtomicLong(0);

    @Autowired
    public BillingEventStreamSubscriber(
            BillingService billingService,
            @Autowired(required = false) EventPublisher eventPublisher
    ) {
        this.billingService = billingService;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void initSubscription() {
        if (eventPublisher != null) {
            eventPublisher.subscribe(this::onDomainEvent);
        }
    }

    public void onDomainEvent(DomainEvent<?> event) {
        if (event == null || event.eventType() == null) {
            return;
        }
        if ("auction.win.v1".equals(event.eventType())) {
            handleAuctionWin(event);
        }
    }

    private void handleAuctionWin(DomainEvent<?> event) {
        String tenantId = event.tenantId() != null ? event.tenantId() : "public";
        String auctionId = event.aggregateId() != null
                ? event.aggregateId()
                : (event.eventId() != null ? event.eventId().toString() : UUID.randomUUID().toString());
        Object payload = event.payload();

        String campaignId = extractString(payload, "campaignId", "adv_default");
        String slotId = extractString(payload, "slotId", "pub_default");
        double cpmPrice = extractDouble(payload, "price", 0.0);
        if (cpmPrice <= 0.0) {
            return;
        }

        // CPM (千次展示价格) 折算为单次胜出实际结算金额 (保留 6 位小数)
        BigDecimal totalCharge = BigDecimal.valueOf(cpmPrice)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        BigDecimal publisherShare = totalCharge.multiply(PUBLISHER_SHARE_RATIO)
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal platformFee = totalCharge.subtract(publisherShare);

        Instant now = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        // 1. 借记广告主扣费 (DEBIT)
        billingService.record(new BillingService.BillingEntry(
                "ble_" + UUID.randomUUID().toString().replace("-", ""),
                tenantId,
                campaignId,
                auctionId,
                BillingService.EntryType.ADVERTISER_CHARGE,
                BillingService.EntryDirection.DEBIT,
                totalCharge,
                "USD",
                "win_debit:" + auctionId,
                "OpenRTB Win Charge (CPM=" + cpmPrice + ")",
                now
        ));

        // 2. 贷记媒体分成收益 (CREDIT)
        billingService.record(new BillingService.BillingEntry(
                "ble_" + UUID.randomUUID().toString().replace("-", ""),
                tenantId,
                slotId,
                auctionId,
                BillingService.EntryType.PUBLISHER_REVENUE,
                BillingService.EntryDirection.CREDIT,
                publisherShare,
                "USD",
                "win_pub_credit:" + auctionId,
                "Publisher 80% Revenue Share",
                now
        ));

        // 3. 贷记平台交易抽成 (CREDIT)
        billingService.record(new BillingService.BillingEntry(
                "ble_" + UUID.randomUUID().toString().replace("-", ""),
                tenantId,
                "platform_treasury",
                auctionId,
                BillingService.EntryType.PLATFORM_FEE,
                BillingService.EntryDirection.CREDIT,
                platformFee,
                "USD",
                "win_fee_credit:" + auctionId,
                "ADX Platform 20% Take-Rate",
                now
        ));

        processedWinCount.incrementAndGet();
        log.debug("[BillingEventSubscriber] Recorded balanced double-entry ledger for auctionId={}, charge={}",
                auctionId, totalCharge);
    }

    public long getProcessedWinCount() {
        return processedWinCount.get();
    }

    private static String extractString(Object obj, String field, String fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Map<?, ?> map) {
            Object val = map.get(field);
            return val != null ? String.valueOf(val) : fallback;
        }
        try {
            Method m = obj.getClass().getMethod(field);
            Object val = m.invoke(obj);
            return val != null ? String.valueOf(val) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double extractDouble(Object obj, String field, double fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Map<?, ?> map) {
            Object val = map.get(field);
            if (val instanceof Number num) return num.doubleValue();
            if (val != null) {
                try {
                    return Double.parseDouble(String.valueOf(val));
                } catch (NumberFormatException ignored) {
                }
            }
            return fallback;
        }
        try {
            Method m = obj.getClass().getMethod(field);
            Object val = m.invoke(obj);
            if (val instanceof Number num) return num.doubleValue();
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
