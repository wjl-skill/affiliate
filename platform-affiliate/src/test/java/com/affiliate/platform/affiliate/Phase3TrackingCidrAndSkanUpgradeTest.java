package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.SkanAttributionResult;
import com.affiliate.platform.affiliate.domain.SkanPostbackPayload;
import com.affiliate.platform.affiliate.service.AffiliateAntiFraudEngine;
import com.affiliate.platform.affiliate.service.ProbabilisticAttributionEngine;
import com.affiliate.platform.affiliate.service.SkanAttributionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("Phase 3 Tracking 升级验证：IPv6 /48 + Redis 分布式概率归因、双栈 CIDR 反欺诈与 SKAN 4.0 序列防篡改测试")
class Phase3TrackingCidrAndSkanUpgradeTest {

    @Test
    @DisplayName("1. 验证 IPv6 /48 路由前缀提取与跨节点 Redis 分布式概率指纹归因共享")
    @SuppressWarnings("unchecked")
    void testIpv6SubnetAndRedisDistributedProbabilisticAttribution() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ListOperations<String, String> mockListOps = Mockito.mock(ListOperations.class);
        List<String> sharedRedisList = new ArrayList<>();

        when(mockRedis.opsForList()).thenReturn(mockListOps);
        when(mockListOps.rightPush(anyString(), anyString())).thenAnswer(inv -> {
            sharedRedisList.add(inv.getArgument(1));
            return (long) sharedRedisList.size();
        });
        when(mockListOps.range(anyString(), anyLong(), anyLong())).thenReturn(sharedRedisList);

        // 节点 A (Tracker 节点) 登记 IPv6 点击指纹（使用压缩格式 2001:db8:85a3::1）
        ProbabilisticAttributionEngine nodeATracker = new ProbabilisticAttributionEngine(mockRedis);
        Instant now = Instant.now();
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15";
        ClickSession ipv6Session = new ClickSession(
                "clk_ipv6_nodeA", "tenant-1", "off_ipv6_01", "aff_ipv6_88",
                "sub_tiktok", "ad_02", null, null, null,
                "2001:db8:85a3::1", ua, "US", 1, now, now.plusSeconds(86400)
        );
        nodeATracker.registerClickFingerprint(ipv6Session);

        // 节点 B (Postback 节点，本地内存池为空，仅通过 Redis 共享池读取) 收到同 /48 前缀的完整格式 IPv6 转化上报
        ProbabilisticAttributionEngine nodeBPostback = new ProbabilisticAttributionEngine(mockRedis);
        assertEquals(
                nodeATracker.extractSubnet("2001:db8:85a3::1"),
                nodeBPostback.extractSubnet("2001:0db8:85a3:0000:0000:8a2e:0370:7334"),
                "同一 /48 路由前缀的压缩与非压缩 IPv6 地址应解析出完全相同的子网 Key"
        );

        ProbabilisticAttributionEngine.ProbabilisticMatchResult matchResult = nodeBPostback.matchAttribution(
                "off_ipv6_01", "2001:0db8:85a3:0000:0000:8a2e:0370:7334", ua, "US", 1
        );

        assertTrue(matchResult.matched(), "节点 B 应通过 Redis 分布式指纹池与 IPv6 /48 子网成功匹配节点 A 的点击会话");
        assertEquals(0.80, matchResult.confidenceScore(), 0.001);
        assertEquals("clk_ipv6_nodeA", matchResult.matchedSession().clickId());
    }

    @Test
    @DisplayName("2. 验证反欺诈引擎 IPv4/IPv6 双栈标准 CIDR 掩码网段匹配")
    void testDualStackCidrMatchingInAntiFraudEngine() {
        assertTrue(AffiliateAntiFraudEngine.ipInCidr("18.160.25.10", "18.128.0.0/9"));
        assertFalse(AffiliateAntiFraudEngine.ipInCidr("18.64.25.10", "18.128.0.0/9"));

        assertTrue(AffiliateAntiFraudEngine.ipInCidr("2600:1f18:400:1::99", "2600:1f18::/32"));
        assertFalse(AffiliateAntiFraudEngine.ipInCidr("2600:1f19:400:1::99", "2600:1f18::/32"));

        AffiliateAntiFraudEngine engine = new AffiliateAntiFraudEngine();
        engine.addDatacenterCidr("198.18.0.0/15");
        assertTrue(engine.isDatacenterIp("198.19.254.12"));
        assertTrue(engine.isDatacenterIp("2600:1f18:abc::1"));
        assertFalse(engine.isDatacenterIp("8.8.4.4"));
    }

    @Test
    @DisplayName("3. 验证 SKAN 4.0 三阶段时间窗序列单调递增校验、CV 防倒退与签名核验钩子")
    void testSkanSequenceProgressionAndSignatureVerification() {
        SkanAttributionEngine skanEngine = new SkanAttributionEngine();
        skanEngine.setSignatureVerifier(payload ->
                payload.attributionSignature() != null && payload.attributionSignature().startsWith("MEUCIQ")
        );

        String txId = "550e8400-e29b-41d4-a716-446655449999";

        // 1) Window 0 (0-2d) 首次上报：seqIndex = 1, fineCv = 25 -> 正常 PROCESSED
        SkanPostbackPayload win1 = new SkanPostbackPayload(
                "4.0", "apple.skadnetwork", "8421", "123456789", txId,
                1, 25, "medium", 1, "MEUCIQ_valid_sig_w1"
        );
        SkanAttributionResult res1 = skanEngine.processPostback("tenant_skan", win1);
        assertEquals("PROCESSED", res1.status());

        // 2) 异常倒退上报：同一 txId 随后收到 seqIndex = 0 (窗口倒退) -> 识别为 SEQUENCE_ANOMALY
        SkanPostbackPayload regressedWin = new SkanPostbackPayload(
                "4.0", "apple.skadnetwork", "8421", "123456789", txId,
                0, 20, "low", 1, "MEUCIQ_valid_sig_w0"
        );
        SkanAttributionResult resRegressed = skanEngine.processPostback("tenant_skan", regressedWin);
        assertEquals("SEQUENCE_ANOMALY", resRegressed.status());
        assertEquals(1L, skanEngine.getSequenceAnomalyCount());

        // 3) 伪造签名上报 -> 抛出 SecurityException 拦截
        SkanPostbackPayload forgedPayload = new SkanPostbackPayload(
                "4.0", "apple.skadnetwork", "8421", "123456789", "tx_forged_01",
                0, 50, "high", 1, "INVALID_SIGNATURE"
        );
        assertThrows(SecurityException.class, () -> skanEngine.processPostback("tenant_skan", forgedPayload));
    }
}
