package com.affiliate.platform.dsp;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DSP 广告计划智能竞价与投产优化顾问服务 (AI Campaign Advisory Service)
 */
@Service
public class AiCampaignAdvisoryService {

    private static final Logger log = LoggerFactory.getLogger(AiCampaignAdvisoryService.class);

    private final AiOrchestratorService aiOrchestratorService;
    private final DeepCtrCvrInferenceEngine inferenceEngine;

    @Autowired
    public AiCampaignAdvisoryService(
            @Autowired(required = false) AiOrchestratorService aiOrchestratorService,
            @Autowired(required = false) DeepCtrCvrInferenceEngine inferenceEngine
    ) {
        this.aiOrchestratorService = aiOrchestratorService;
        this.inferenceEngine = inferenceEngine != null ? inferenceEngine : new DeepCtrCvrInferenceEngine();
    }

    public AiCampaignAdvisoryService() {
        this(null, null);
    }

    public record CampaignAdvisoryResult(
            String campaignId,
            String tenantId,
            BigDecimal currentBid,
            BigDecimal floorPrice,
            double estimatedCtr,
            double estimatedCvr,
            double winRate,
            String aiRecommendation,
            Instant generatedAt
    ) {}

    public CampaignAdvisoryResult adviseCampaign(
            Campaign campaign,
            TrafficContext context,
            BigDecimal floorPrice
    ) {
        String campId = campaign != null ? campaign.id() : "camp_unknown";
        String tenantId = campaign != null && campaign.advertiserId() != null ? campaign.advertiserId() : "default";
        BigDecimal baseBid = campaign != null && campaign.maxBid() != null ? campaign.maxBid() : BigDecimal.valueOf(1.50);
        BigDecimal floor = floorPrice != null ? floorPrice : BigDecimal.valueOf(0.50);

        var inference = inferenceEngine.predict(context, campaign, floor);
        double ctr = inference.pCtr();
        double cvr = inference.pCvr();
        double winRate = 0.65; // 基准暗标胜出率估算

        String advice;
        if (aiOrchestratorService != null) {
            try {
                advice = aiOrchestratorService.adviseBidOptimizationWithAi(
                        tenantId,
                        campId,
                        baseBid,
                        ctr,
                        cvr,
                        floor,
                        winRate
                );
            } catch (Exception e) {
                log.warn("[AiCampaignAdvisory] AI advisory generation fallback: {}", e.getMessage());
                advice = generateHeuristicAdvice(campId, baseBid, ctr, cvr, floor);
            }
        } else {
            advice = generateHeuristicAdvice(campId, baseBid, ctr, cvr, floor);
        }

        return new CampaignAdvisoryResult(
                campId,
                tenantId,
                baseBid,
                floor,
                ctr,
                cvr,
                winRate,
                advice,
                Instant.now()
        );
    }

    private String generateHeuristicAdvice(String campId, BigDecimal bid, double ctr, double cvr, BigDecimal floor) {
        return String.format(
                "【DSP 智能出价启发式建议】计划 %s 当前出价 $%s (底价 $%s)。预估 CTR: %.2f%%, CVR: %.2f%%。" +
                "建议针对高转化 WiFi 流量上浮 10%% 竞价以保证曝光，低价值时段配合 Bid Shading 压缩 15%% 成本。",
                campId, bid, floor, ctr * 100, cvr * 100
        );
    }
}
