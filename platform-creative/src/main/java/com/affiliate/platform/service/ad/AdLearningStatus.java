package com.affiliate.platform.service.ad;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 广告学习期判定结果
 */
public record AdLearningStatus(
        boolean isLearning,
        boolean shouldLockBid,
        boolean shouldCircuitBreak,
        String reason
) {}
