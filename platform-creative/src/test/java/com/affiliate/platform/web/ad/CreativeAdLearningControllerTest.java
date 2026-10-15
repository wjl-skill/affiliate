package com.affiliate.platform.web.ad;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.service.ad.AdLearningStatus;
import com.affiliate.platform.service.ad.impl.AdLearningRuleEngineImpl;
import com.affiliate.platform.service.ad.impl.CreativeDspLinkageServiceImpl;
import com.affiliate.platform.web.ad.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class CreativeAdLearningControllerTest {

    private CreativeAdLearningController controller;

    @BeforeEach
    void setUp() {
        controller = new CreativeAdLearningController(
                new AdLearningRuleEngineImpl(),
                new CreativeDspLinkageServiceImpl()
        );
    }

    @Test
    void testEvaluateLearningStatusLearning() {
        // Just started yesterday, low cost
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        AdLearningEvaluationRequest req = new AdLearningEvaluationRequest(
                yesterday,
                BigDecimal.valueOf(20.00),
                MaterialQualityLevel.POTENTIAL,
                3,
                BigDecimal.valueOf(300.00)
        );
        AdLearningStatus status = controller.evaluateLearningStatus(req);
        assertNotNull(status);
        assertTrue(status.isLearning());
        assertTrue(status.shouldLockBid());
        assertFalse(status.shouldCircuitBreak());
        assertTrue(status.reason().contains("处于模型探索学习期内"));
    }

    @Test
    void testEvaluateLearningStatusLearnedSuccess() {
        // Spent 500 (over 300 threshold), time 4 days (over 3 days)
        Instant fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS);
        AdLearningEvaluationRequest req = new AdLearningEvaluationRequest(
                fourDaysAgo,
                BigDecimal.valueOf(500.00),
                MaterialQualityLevel.STAR,
                3,
                BigDecimal.valueOf(300.00)
        );
        AdLearningStatus status = controller.evaluateLearningStatus(req);
        assertNotNull(status);
        assertFalse(status.isLearning());
        assertFalse(status.shouldLockBid());
        assertFalse(status.shouldCircuitBreak());
        assertTrue(status.reason().contains("已平稳度过学习期") || status.reason().contains("算法模型探索完成"));
    }

    @Test
    void testEvaluateLearningStatusFailedBlackhole() {
        // Blackhole material inside learning window -> triggers circuit break
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        AdLearningEvaluationRequest req = new AdLearningEvaluationRequest(
                yesterday,
                BigDecimal.valueOf(20.00),
                MaterialQualityLevel.BLACKHOLE,
                3,
                BigDecimal.valueOf(300.00)
        );
        AdLearningStatus status = controller.evaluateLearningStatus(req);
        assertNotNull(status);
        assertTrue(status.isLearning());
        assertTrue(status.shouldLockBid());
        assertTrue(status.shouldCircuitBreak());
        assertTrue(status.reason().contains("触发自动熔断下线预警"));
    }

    @Test
    void testDspMultiplierAndSmartLinkScore() {
        // STAR material: multiplier 1.25, eligible
        DspMultiplierResponse starResp = controller.getDspMultiplier(new DspMultiplierRequest(MaterialQualityLevel.STAR));
        assertEquals(1.25, starResp.qualityMultiplier());
        assertTrue(starResp.eligibleForDspBidding());

        // BLACKHOLE material: ineligible
        DspMultiplierResponse bhResp = controller.getDspMultiplier(new DspMultiplierRequest(MaterialQualityLevel.BLACKHOLE));
        assertEquals(0.0, bhResp.qualityMultiplier());
        assertFalse(bhResp.eligibleForDspBidding());

        // SmartLink Score for STAR: epc * capFactor * 1.50
        SmartLinkScoreRequest scoreReq = new SmartLinkScoreRequest(
                BigDecimal.valueOf(2.50),
                1.0,
                MaterialQualityLevel.STAR
        );
        SmartLinkScoreResponse scoreResp = controller.calculateSmartLinkScore(scoreReq);
        assertEquals(0, BigDecimal.valueOf(3.25).compareTo(scoreResp.routingScore()));
    }
}
