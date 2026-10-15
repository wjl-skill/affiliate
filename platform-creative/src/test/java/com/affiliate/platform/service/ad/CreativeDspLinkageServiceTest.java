package com.affiliate.platform.service.ad;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.service.ad.impl.CreativeDspLinkageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreativeDspLinkageService Tests - DSP Bid Shading & SmartLink TDS")
class CreativeDspLinkageServiceTest {

    private CreativeDspLinkageServiceImpl linkageService;

    @BeforeEach
    void setUp() {
        linkageService = new CreativeDspLinkageServiceImpl();
    }

    @Test
    @DisplayName("Should assign correct DSP quality multipliers")
    void testDspQualityMultipliers() {
        assertEquals(1.25, linkageService.getDspQualityMultiplier(MaterialQualityLevel.STAR));
        assertEquals(1.10, linkageService.getDspQualityMultiplier(MaterialQualityLevel.POTENTIAL));
        assertEquals(1.00, linkageService.getDspQualityMultiplier(MaterialQualityLevel.CORE));
        assertEquals(0.50, linkageService.getDspQualityMultiplier(MaterialQualityLevel.RISK));
        assertEquals(0.00, linkageService.getDspQualityMultiplier(MaterialQualityLevel.BLACKHOLE));
        assertEquals(1.00, linkageService.getDspQualityMultiplier(MaterialQualityLevel.UNRATED));

        assertTrue(linkageService.isEligibleForDspBidding(MaterialQualityLevel.STAR));
        assertFalse(linkageService.isEligibleForDspBidding(MaterialQualityLevel.BLACKHOLE));
    }

    @Test
    @DisplayName("Should grant +30% routing boost to STAR materials in SmartLink TDS")
    void testSmartLinkRoutingScore() {
        BigDecimal epc = BigDecimal.valueOf(2.50);
        double capFactor = 1.0;

        BigDecimal normalScore = linkageService.calculateSmartLinkRoutingScore(epc, capFactor, MaterialQualityLevel.CORE);
        assertEquals(new BigDecimal("2.5000"), normalScore);

        BigDecimal starScore = linkageService.calculateSmartLinkRoutingScore(epc, capFactor, MaterialQualityLevel.STAR);
        // 2.50 * 1.30 = 3.25
        assertEquals(new BigDecimal("3.2500"), starScore);
    }
}
