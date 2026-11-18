package com.affiliate.platform.integration;

import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.ssp.HeaderBiddingOrchestrator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * 外部媒体变现连接器 -> SSP Header Bidding 统一适配器桥接器
 * <p>
 * 将 {@link AdPlatformConnector}（Google GAM、Meta Audience Network、TikTok Pangle）适配为
 * {@link HeaderBiddingOrchestrator.DemandPartnerAdapter}，使其直接纳入 SSP Top-K 优选、熔断器与超时屏障调度体系。
 */
public class ConnectorDemandPartnerAdapter implements HeaderBiddingOrchestrator.DemandPartnerAdapter {

    private final AdPlatformConnector connector;
    private final PartnerConnection connection;
    private final BigDecimal fallbackFloorCpm;
    private final long queryTimeoutMs;

    public ConnectorDemandPartnerAdapter(
            AdPlatformConnector connector,
            PartnerConnection connection,
            BigDecimal fallbackFloorCpm
    ) {
        this(connector, connection, fallbackFloorCpm, 60L);
    }

    public ConnectorDemandPartnerAdapter(
            AdPlatformConnector connector,
            PartnerConnection connection,
            BigDecimal fallbackFloorCpm,
            long queryTimeoutMs
    ) {
        if (connector == null) {
            throw new IllegalArgumentException("connector must not be null");
        }
        this.connector = connector;
        this.connection = connection;
        this.fallbackFloorCpm = (fallbackFloorCpm != null && fallbackFloorCpm.signum() > 0)
                ? fallbackFloorCpm
                : new BigDecimal("1.50");
        this.queryTimeoutMs = queryTimeoutMs > 0 ? queryTimeoutMs : 60L;
    }

    @Override
    public String partnerId() {
        return "MEDIA_" + MediaPlatformConnectorRegistry.normalizeProvider(connector.provider());
    }

    @Override
    public HeaderBiddingOrchestrator.BidOffer requestBid(String slotId) {
        return requestBid(new HeaderBiddingOrchestrator.HeaderBidRequestContext(
                slotId, null, "US", 1, fallbackFloorCpm, fallbackFloorCpm, queryTimeoutMs, java.util.Set.of()
        ));
    }

    @Override
    public HeaderBiddingOrchestrator.BidOffer requestBid(HeaderBiddingOrchestrator.HeaderBidRequestContext context) {
        String slotId = context != null && context.slotId() != null ? context.slotId() : "slot_default";
        String accountId = (connection != null && connection.settings() != null)
                ? connection.settings().getOrDefault("accountId", connection.id())
                : "default_acct";
        try {
            LocalDate today = LocalDate.now();
            AdPerformanceQuery query = new AdPerformanceQuery(
                    accountId,
                    today.minusDays(1).toString(),
                    today.toString()
            );
            long timeout = (context != null && context.timeoutMs() > 0)
                    ? Math.min(queryTimeoutMs, context.timeoutMs())
                    : queryTimeoutMs;
            AdPerformanceReport report = connector.queryPerformance(query).get(timeout, TimeUnit.MILLISECONDS);

            BigDecimal estimatedCpm = fallbackFloorCpm;
            if (report != null && report.totalImpressions() > 0 && report.totalCost() != null && report.totalCost().signum() > 0) {
                estimatedCpm = report.totalCost()
                        .multiply(BigDecimal.valueOf(1000))
                        .divide(BigDecimal.valueOf(report.totalImpressions()), 4, RoundingMode.HALF_UP);
            }
            if (context != null && context.hardFloor() != null && estimatedCpm.compareTo(context.hardFloor()) < 0) {
                return null;
            }
            String adHtml = "<div data-provider=\"" + partnerId() + "\" data-slot=\"" + slotId + "\">Ad</div>";
            return new HeaderBiddingOrchestrator.BidOffer(
                    partnerId(),
                    "cr_" + partnerId().toLowerCase() + "_" + slotId,
                    estimatedCpm,
                    adHtml,
                    "USD"
            );
        } catch (Exception ex) {
            return null;
        }
    }
}
