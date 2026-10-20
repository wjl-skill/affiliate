package com.affiliate.platform.dsp;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class AiCampaignAdvisoryServiceTest {

    @Test
    void testAiAdvisoryGenerationWithMockOrchestrator() {
        AiOrchestratorService mockOrchestrator = Mockito.mock(AiOrchestratorService.class);
        Mockito.when(mockOrchestrator.adviseBidOptimizationWithAi(any(), any(), any(), anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn("AI Strategy: Shading factor 0.85, target eCPA $12.50, raise WiFi bid by 15%");

        AiCampaignAdvisoryService advisoryService = new AiCampaignAdvisoryService(mockOrchestrator, new DeepCtrCvrInferenceEngine());

        Campaign campaign = new Campaign(
                "camp_001",
                "adv-1",
                "US Target Campaign",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(10),
                BigDecimal.valueOf(500.0),
                BigDecimal.valueOf(2.50),
                Set.of("news.com"),
                Set.of(1, 2),
                Campaign.Status.ACTIVE,
                Instant.now()
        );

        TrafficContext ctx = TrafficContext.of("news.com", 1, LocalDate.now());
        var result = advisoryService.adviseCampaign(campaign, ctx, BigDecimal.valueOf(0.80));

        assertNotNull(result);
        assertEquals("camp_001", result.campaignId());
        assertEquals(BigDecimal.valueOf(2.50), result.currentBid());
        assertTrue(result.aiRecommendation().contains("Shading factor 0.85"));
    }

    @Test
    void testAdvisoryFallbackWhenOrchestratorNull() {
        AiCampaignAdvisoryService advisoryService = new AiCampaignAdvisoryService(null, null);

        Campaign campaign = new Campaign(
                "camp_002",
                "adv-2",
                "Android Target Campaign",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(10),
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(1.80),
                Set.of(),
                Set.of(1),
                Campaign.Status.ACTIVE,
                Instant.now()
        );

        var result = advisoryService.adviseCampaign(campaign, null, BigDecimal.valueOf(0.50));
        assertNotNull(result);
        assertTrue(result.aiRecommendation().contains("DSP 智能出价启发式建议"));
    }
}
