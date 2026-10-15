package com.affiliate.platform.service.ad;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.service.ad.impl.AdLearningRuleEngineImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdLearningRuleEngine Tests - Learning Phase Rules & Circuit Break")
class AdLearningRuleEngineTest {

    private AdLearningRuleEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new AdLearningRuleEngineImpl();
    }

    @Test
    @DisplayName("Should return not started when firstMetricTime is null")
    void shouldReturnNotStartedWhenNoMetric() {
        AdLearningStatus status = engine.evaluateLearningPhase(
                null, BigDecimal.ZERO, MaterialQualityLevel.UNRATED, 2, BigDecimal.valueOf(50)
        );
        assertFalse(status.isLearning());
        assertFalse(status.shouldLockBid());
        assertFalse(status.shouldCircuitBreak());
        assertTrue(status.reason().contains("未开始"));
    }

    @Test
    @DisplayName("Should lock bids when within learning phase (within 48h and cost <= 50)")
    void shouldLockBidsDuringLearningPhase() {
        Instant firstMetric = Instant.now().minus(12, ChronoUnit.HOURS);
        BigDecimal cost = BigDecimal.valueOf(25.00);

        AdLearningStatus status = engine.evaluateLearningPhase(
                firstMetric, cost, MaterialQualityLevel.POTENTIAL, 2, BigDecimal.valueOf(50)
        );
        assertTrue(status.isLearning());
        assertTrue(status.shouldLockBid());
        assertFalse(status.shouldCircuitBreak());
        assertTrue(status.reason().contains("锁死出价"));
    }

    @Test
    @DisplayName("Should trigger circuit break when hitting BLACKHOLE during learning phase")
    void shouldCircuitBreakOnBlackholeDuringLearning() {
        Instant firstMetric = Instant.now().minus(20, ChronoUnit.HOURS);
        BigDecimal cost = BigDecimal.valueOf(45.00);

        AdLearningStatus status = engine.evaluateLearningPhase(
                firstMetric, cost, MaterialQualityLevel.BLACKHOLE, 2, BigDecimal.valueOf(50)
        );
        assertTrue(status.isLearning());
        assertTrue(status.shouldCircuitBreak());
        assertTrue(status.reason().contains("自动熔断下线预警"));
    }

    @Test
    @DisplayName("Should exit learning phase when time exceeds 48h or cost exceeds threshold")
    void shouldExitLearningPhase() {
        // Exceed time
        Instant firstMetricExpired = Instant.now().minus(50, ChronoUnit.HOURS);
        AdLearningStatus timeExpiredStatus = engine.evaluateLearningPhase(
                firstMetricExpired, BigDecimal.valueOf(30), MaterialQualityLevel.CORE, 2, BigDecimal.valueOf(50)
        );
        assertFalse(timeExpiredStatus.isLearning());
        assertFalse(timeExpiredStatus.shouldLockBid());

        // Exceed cost
        Instant firstMetricRecent = Instant.now().minus(10, ChronoUnit.HOURS);
        AdLearningStatus costExceededStatus = engine.evaluateLearningPhase(
                firstMetricRecent, BigDecimal.valueOf(65.00), MaterialQualityLevel.CORE, 2, BigDecimal.valueOf(50)
        );
        assertFalse(costExceededStatus.isLearning());
        assertFalse(costExceededStatus.shouldLockBid());
    }
}
