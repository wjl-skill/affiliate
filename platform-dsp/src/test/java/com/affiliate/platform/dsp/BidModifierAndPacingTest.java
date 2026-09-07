package com.affiliate.platform.dsp;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BidModifierAndPacingTest {

    @Test
    void bidModifierMultipliersCalculation() {
        BidModifierEngine engine = new BidModifierEngine();

        // 基础出价 $2.00 CPM
        BigDecimal baseBid = new BigDecimal("2.0000");

        // 场景 1: iOS (1.35x), US (1.50x), 晚间 20:00 高峰 (1.25x), 高质量位 (1.20x)
        // 期望 = 2.00 * 1.35 * 1.50 * 1.25 * 1.20 = 2.00 * 3.0375 = 6.0750
        BigDecimal adjusted = engine.calculateAdjustedBid(
                baseBid, 2, "US", 20, new BigDecimal("1.20"), new BigDecimal("10.00")
        );

        assertEquals(0, new BigDecimal("6.0750").compareTo(adjusted));

        // 场景 2: 熔断上限限制 (maxLimit = $5.00)
        BigDecimal capped = engine.calculateAdjustedBid(
                baseBid, 2, "US", 20, new BigDecimal("1.20"), new BigDecimal("5.00")
        );
        assertEquals(0, new BigDecimal("5.00").compareTo(capped));

        // 场景 3: 深夜低谷 (03:00) 抑价
        BigDecimal nightBid = engine.calculateAdjustedBid(
                baseBid, 1, "US", 3, new BigDecimal("1.00"), null
        );
        // 2.00 * 1.0 (Android) * 1.50 (US) * 0.70 (03:00) = 2.1000
        assertEquals(0, new BigDecimal("2.1000").compareTo(nightBid));
    }
}
