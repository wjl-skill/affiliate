package com.affiliate.platform.integration;

import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.ssp.HeaderBiddingOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase B 验证：外部媒体连接器桥接 SSP Header Bidding 并发询价测试")
class PhaseBMediaConnectorHeaderBiddingTest {

    @Test
    @DisplayName("验证 MediaPlatformConnectorRegistry 封装连接器并参与 HeaderBiddingOrchestrator 竞价")
    void testMediaConnectorsParticipateInHeaderBidding() {
        AdPlatformConnector gamConnector = new AdPlatformConnector() {
            @Override
            public String provider() {
                return "GAM";
            }

            @Override
            public CompletableFuture<SyncResult> sync(PartnerConnection connection) {
                return CompletableFuture.completedFuture(new SyncResult("GAM", 1, 0, "ok"));
            }

            @Override
            public CompletableFuture<AdPerformanceReport> queryPerformance(AdPerformanceQuery query) {
                // 1000 次曝光收入 $4.20 -> 估算 eCPM = $4.20
                return CompletableFuture.completedFuture(new AdPerformanceReport(
                        "GAM", "gam_acct", List.of(), new BigDecimal("4.20"), 1000L, 40L, 2L
                ));
            }
        };

        AdPlatformConnector pangleConnector = new AdPlatformConnector() {
            @Override
            public String provider() {
                return "PANGLE";
            }

            @Override
            public CompletableFuture<SyncResult> sync(PartnerConnection connection) {
                return CompletableFuture.completedFuture(new SyncResult("PANGLE", 1, 0, "ok"));
            }

            @Override
            public CompletableFuture<AdPerformanceReport> queryPerformance(AdPerformanceQuery query) {
                // 1000 次曝光收入 $2.80 -> 估算 eCPM = $2.80
                return CompletableFuture.completedFuture(new AdPerformanceReport(
                        "PANGLE", "pangle_acct", List.of(), new BigDecimal("2.80"), 1000L, 25L, 1L
                ));
            }
        };

        MediaPlatformConnectorRegistry registry = new MediaPlatformConnectorRegistry(List.of(gamConnector, pangleConnector));
        PartnerConnection gamConn = new PartnerConnection(
                "conn_gam", "GAM", com.affiliate.platform.domain.Enums.SupplyType.SSP, "https://gam.example.com",
                Map.of("accountId", "gam_acct"), com.affiliate.platform.domain.Enums.ConnectionStatus.ACTIVE, Instant.now());
        PartnerConnection pangleConn = new PartnerConnection(
                "conn_pangle", "PANGLE", com.affiliate.platform.domain.Enums.SupplyType.SSP, "https://pangle.example.com",
                Map.of("accountId", "pangle_acct"), com.affiliate.platform.domain.Enums.ConnectionStatus.ACTIVE, Instant.now());

        List<HeaderBiddingOrchestrator.DemandPartnerAdapter> adapters = registry.buildDemandPartnerAdapters(
                Map.of("GAM", gamConn, "PANGLE", pangleConn),
                new BigDecimal("1.50")
        );
        assertEquals(2, adapters.size());

        HeaderBiddingOrchestrator orchestrator = new HeaderBiddingOrchestrator();
        HeaderBiddingOrchestrator.HeaderBiddingAuctionResult result = orchestrator.orchestrateAuction(
                "slot_banner_01", new BigDecimal("2.00"), adapters, 200L
        );

        assertTrue(result.hasWinner());
        assertEquals("MEDIA_GAM", result.winningBid().bidderId());
        assertEquals(0, new BigDecimal("4.2000").compareTo(result.clearingPrice()));
    }
}
