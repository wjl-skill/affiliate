package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.SkanAttributionResult;
import com.affiliate.platform.affiliate.domain.SkanPostbackPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkanAttributionEngineTest {

    private SkanAttributionEngine skanEngine;

    @BeforeEach
    void setUp() {
        skanEngine = new SkanAttributionEngine();
    }

    @Test
    void testParseSourceIdentifierHierarchy() {
        // 4 位：8421 -> Campaign: 21, Placement: 4, Creative: 8
        SkanAttributionEngine.SourceIdComponents c4 = skanEngine.parseSourceIdentifier("8421");
        assertEquals("21", c4.campaignId());
        assertEquals("4", c4.placementHint());
        assertEquals("8", c4.creativeHint());

        // 2 位：21 -> Campaign: 21, Placement: 0, Creative: 0
        SkanAttributionEngine.SourceIdComponents c2 = skanEngine.parseSourceIdentifier("21");
        assertEquals("21", c2.campaignId());
        assertEquals("0", c2.placementHint());
        assertEquals("0", c2.creativeHint());
    }

    @Test
    void testConversionValueDecoding() {
        // 细粒度 (Fine CV)
        BigDecimal revLow = skanEngine.decodeEstimatedRevenue(0, 4, null);
        assertEquals(BigDecimal.valueOf(2.0).setScale(4), revLow);

        BigDecimal revMid = skanEngine.decodeEstimatedRevenue(0, 15, null);
        assertEquals(BigDecimal.valueOf(10.0).setScale(4), revMid);

        // 粗粒度 (Coarse CV)
        BigDecimal revHighCoarse = skanEngine.decodeEstimatedRevenue(1, null, "high");
        assertEquals(SkanAttributionEngine.COARSE_HIGH_VALUE, revHighCoarse);

        BigDecimal revLowCoarse = skanEngine.decodeEstimatedRevenue(2, null, "low");
        assertEquals(SkanAttributionEngine.COARSE_LOW_VALUE, revLowCoarse);
    }

    @Test
    void testProcessPostbackAndIdempotency() {
        String tenantId = "tenant_ios_01";
        String txId = "550e8400-e29b-41d4-a716-446655440000";

        SkanPostbackPayload payload = new SkanPostbackPayload(
                "4.0",
                "apple.test.skadnetwork",
                "9355", // Creative: 9, Placement: 3, Campaign: 55
                "id123456789",
                txId,
                0,
                20,
                null,
                1,
                "apple_signature_dummy"
        );

        SkanAttributionResult result1 = skanEngine.processPostback(tenantId, payload);
        assertNotNull(result1);
        assertEquals("55", result1.campaignId());
        assertEquals("3", result1.placementHint());
        assertEquals("9", result1.creativeHint());
        assertTrue(result1.estimatedRevenue().compareTo(BigDecimal.ZERO) > 0);

        // 重放测试：相同 txId + seqIndex 必须幂等返回
        SkanAttributionResult result2 = skanEngine.processPostback(tenantId, payload);
        assertEquals(result1.id(), result2.id());
        assertEquals(result1.estimatedRevenue(), result2.estimatedRevenue());
        assertEquals(1, skanEngine.getProcessedCount());

        // 广告网络索引列表查询
        List<SkanAttributionResult> list = skanEngine.listByAdNetwork(tenantId, "apple.test.skadnetwork");
        assertEquals(1, list.size());
    }
}
