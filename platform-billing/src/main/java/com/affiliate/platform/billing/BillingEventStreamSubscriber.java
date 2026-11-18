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
    private static final double DEFAULT_PLATFORM_FEE_RATE = 0.20;

    private final BillingService billingService;
    private final EventPublisher eventPublisher;
    private final RevenueShareService revenueShareService;
    private final java.util.concurrent.ConcurrentMap<String, Double> platformFeeRates = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong processedWinCount = new AtomicLong(0);
    private final AtomicLong processedConversionCount = new AtomicLong(0);

    @Autowired
    public BillingEventStreamSubscriber(
            BillingService billingService,
            @Autowired(required = false) EventPublisher eventPublisher
    ) {
        this(billingService, eventPublisher, new RevenueShareService());
    }

    public BillingEventStreamSubscriber(
            BillingService billingService,
            EventPublisher eventPublisher,
            RevenueShareService revenueShareService
    ) {
        this.billingService = billingService;
        this.eventPublisher = eventPublisher;
        this.revenueShareService = revenueShareService != null ? revenueShareService : new RevenueShareService();
    }

    public void setPlatformFeeRate(String tenantId, String slotId, double feeRate) {
        String key = (tenantId != null ? tenantId : "public") + ":" + (slotId != null ? slotId : "*");
        platformFeeRates.put(key, Math.max(0.0, Math.min(1.0, feeRate)));
    }

    public double resolvePlatformFeeRate(String tenantId, String slotId) {
        String t = tenantId != null ? tenantId : "public";
        if (slotId != null && platformFeeRates.containsKey(t + ":" + slotId)) {
            return platformFeeRates.get(t + ":" + slotId);
        }
        return platformFeeRates.getOrDefault(t + ":*", DEFAULT_PLATFORM_FEE_RATE);
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
        } else if ("conversion.approved.v1".equals(event.eventType())) {
            handleConversionApproved(event);
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

        // CPM (千次展示价格) 折算为单次胜出实际结算金额 (保留 6 位小数)，并按动态分成比例拆解
        BigDecimal totalCharge = BigDecimal.valueOf(cpmPrice)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        double feeRate = resolvePlatformFeeRate(tenantId, slotId);
        RevenueShareService.PayoutSplit split = revenueShareService.split(totalCharge, feeRate, 6);
        BigDecimal platformFee = split.platformFee();
        BigDecimal publisherShare = split.publisherPayout();
        int pubPct = (int) Math.round((1.0 - feeRate) * 100);
        int feePct = (int) Math.round(feeRate * 100);

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
                "Publisher " + pubPct + "% Revenue Share",
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
                "ADX Platform " + feePct + "% Take-Rate",
                now
        ));

        processedWinCount.incrementAndGet();
        log.debug("[BillingEventSubscriber] Recorded balanced double-entry ledger for auctionId={}, charge={}",
                auctionId, totalCharge);
    }

    private void handleConversionApproved(DomainEvent<?> event) {
        String tenantId = event.tenantId() != null ? event.tenantId() : "public";
        Object payload = event.payload();
        String conversionId = extractString(payload, "conversionId",
                event.aggregateId() != null ? event.aggregateId() : UUID.randomUUID().toString());
        String offerId = extractString(payload, "offerId", extractString(payload, "campaignId", "offer_default"));
        String affiliateId = extractString(payload, "affiliateId", "aff_default");

        double revenueVal = extractDouble(payload, "revenue", 0.0);
        double payoutVal = extractDouble(payload, "payout", 0.0);
        if (revenueVal <= 0.0 && payoutVal <= 0.0) {
            return;
        }

        BigDecimal grossRevenue = BigDecimal.valueOf(Math.max(revenueVal, payoutVal)).setScale(6, RoundingMode.HALF_UP);
        BigDecimal affiliatePayout = BigDecimal.valueOf(Math.max(0.0, payoutVal)).setScale(6, RoundingMode.HALF_UP);
        if (affiliatePayout.compareTo(grossRevenue) > 0) {
            affiliatePayout = grossRevenue;
        }
        BigDecimal platformMargin = grossRevenue.subtract(affiliatePayout);
        Instant now = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        billingService.record(new BillingService.BillingEntry(
                "ble_" + UUID.randomUUID().toString().replace("-", ""),
                tenantId,
                offerId,
                conversionId,
                BillingService.EntryType.ADVERTISER_CHARGE,
                BillingService.EntryDirection.DEBIT,
                grossRevenue,
                "USD",
                "conv_debit:" + conversionId,
                "Affiliate Conversion Charge (" + offerId + ")",
                now
        ));

        if (affiliatePayout.signum() > 0) {
            billingService.record(new BillingService.BillingEntry(
                    "ble_" + UUID.randomUUID().toString().replace("-", ""),
                    tenantId,
                    affiliateId,
                    conversionId,
                    BillingService.EntryType.PUBLISHER_REVENUE,
                    BillingService.EntryDirection.CREDIT,
                    affiliatePayout,
                    "USD",
                    "conv_aff_credit:" + conversionId,
                    "Affiliate Conversion Payout (" + affiliateId + ")",
                    now
            ));
        }

        if (platformMargin.signum() > 0) {
            billingService.record(new BillingService.BillingEntry(
                    "ble_" + UUID.randomUUID().toString().replace("-", ""),
                    tenantId,
                    "platform_treasury",
                    conversionId,
                    BillingService.EntryType.PLATFORM_FEE,
                    BillingService.EntryDirection.CREDIT,
                    platformMargin,
                    "USD",
                    "conv_fee_credit:" + conversionId,
                    "Affiliate Platform Conversion Margin",
                    now
            ));
        }

        processedConversionCount.incrementAndGet();
    }

    public long getProcessedWinCount() {
        return processedWinCount.get();
    }

    public long getProcessedConversionCount() {
        return processedConversionCount.get();
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
