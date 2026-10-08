package com.affiliate.platform.cdp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CdpProductionOptimizationTest {

    @Test
    @DisplayName("CDP 跨触点并查集传递闭包合并与防桥接熔断测试")
    void testIdentityGraphResolutionAndAntiBridging() {
        IdentityGraphEngine engine = new IdentityGraphEngine(10, 0.80);

        // 场景 1: 跨端确定性传递闭包
        // 触点 1: Cookie_A 绑定 Device_1
        // 触点 2: Device_1 绑定 Phone_X
        // 触点 3: Phone_X 绑定 CRM_1001
        engine.link("cookie_a", IdentityGraphService.IdentifierType.DEVICE_ID, "device_1", IdentityGraphService.IdentifierType.DEVICE_ID, 0.95);
        engine.link("device_1", IdentityGraphService.IdentifierType.DEVICE_ID, "phone_x", IdentityGraphService.IdentifierType.PHONE, 1.0);
        engine.link("phone_x", IdentityGraphService.IdentifierType.PHONE, "crm_1001", IdentityGraphService.IdentifierType.CRM_ID, 1.0);

        // 验证传递闭包：所有四个节点归一至同一主根
        String rootCookie = engine.getCanonicalId("cookie_a");
        String rootDevice = engine.getCanonicalId("device_1");
        String rootPhone = engine.getCanonicalId("phone_x");
        String rootCrm = engine.getCanonicalId("crm_1001");

        assertEquals(rootCrm, rootCookie, "Cookie 应打通至高权重的 CRM ID 根");
        assertEquals(rootCrm, rootDevice);
        assertEquals(rootCrm, rootPhone);

        Set<String> cluster = engine.getCluster("cookie_a");
        assertEquals(4, cluster.size());
        assertTrue(cluster.containsAll(List.of("cookie_a", "device_1", "phone_x", "crm_1001")));

        // 场景 2: 防身份过度桥接熔断保护 (Anti-Bridging Guardrail)
        // 模拟公共电脑网吧 IP 或公用终端，超过 maxClusterSize = 10
        for (int i = 1; i <= 15; i++) {
            engine.link("public_kiosk", IdentityGraphService.IdentifierType.DEVICE_ID, "visitor_cookie_" + i, IdentityGraphService.IdentifierType.DEVICE_ID, 0.90);
        }

        // 当关联节点过多时，后续继续连接将被拒绝熔断
        boolean bridgeResult = engine.link("public_kiosk", IdentityGraphService.IdentifierType.DEVICE_ID, "new_visitor_99", IdentityGraphService.IdentifierType.DEVICE_ID, 0.90);
        assertFalse(bridgeResult, "公共设备触碰防桥接阈值应被强行熔断拒绝");
    }

    @Test
    @DisplayName("动态受众圈选与进出事件差分计算测试")
    void testAudienceSegmentEngineDifferential() {
        AudienceSegmentEngine engine = new AudienceSegmentEngine();

        // 注册受众规则 1: 大 R 客户 (高消费 >= 500，且具备 whale_purchaser 标签)
        AudienceSegmentEngine.AudienceSegmentRule whaleRule = new AudienceSegmentEngine.AudienceSegmentRule(
                "seg_whale", "Whale Spenders",
                new BigDecimal("500"), null,
                Set.of("whale_purchaser"), Set.of(), null
        );
        engine.registerRule(whaleRule);

        // 注册受众规则 2: 价格敏感型
        AudienceSegmentEngine.AudienceSegmentRule bargainRule = new AudienceSegmentEngine.AudienceSegmentRule(
                "seg_bargain", "Bargain Hunters",
                null, null,
                Set.of("bargain_hunter"), Set.of(), null
        );
        engine.registerRule(bargainRule);

        // 初始状态：普通访客，未命中大 R
        RealtimeEventTraitEngine.UserBehaviorSnapshot snap1 = new RealtimeEventTraitEngine.UserBehaviorSnapshot(
                "user_1", new BigDecimal("150"), 2, 10, 20, 5,
                "sports", "MOBILE", Set.of("bargain_hunter"),
                Instant.now().minusSeconds(86400), Instant.now()
        );

        AudienceSegmentEngine.SegmentDifferentialResult diff1 = engine.evaluateDifferential("user_1", snap1);
        assertEquals(Set.of("seg_bargain"), diff1.currentSegments());
        assertEquals(Set.of("seg_bargain"), diff1.enteredSegments());
        assertTrue(diff1.exitedSegments().isEmpty());
        assertEquals(1, engine.getSegmentCount("seg_bargain"));
        assertEquals(0, engine.getSegmentCount("seg_whale"));

        // 后续追加大额订单消费，标签更新为 whale_purchaser
        RealtimeEventTraitEngine.UserBehaviorSnapshot snap2 = new RealtimeEventTraitEngine.UserBehaviorSnapshot(
                "user_1", new BigDecimal("800"), 4, 15, 25, 8,
                "luxury", "MOBILE", Set.of("whale_purchaser"),
                Instant.now().minusSeconds(86400), Instant.now()
        );

        AudienceSegmentEngine.SegmentDifferentialResult diff2 = engine.evaluateDifferential("user_1", snap2);
        // 新进入 seg_whale，同时退出 seg_bargain
        assertEquals(Set.of("seg_whale"), diff2.currentSegments());
        assertEquals(Set.of("seg_whale"), diff2.enteredSegments());
        assertEquals(Set.of("seg_bargain"), diff2.exitedSegments());

        assertEquals(1, engine.getSegmentCount("seg_whale"));
        assertEquals(0, engine.getSegmentCount("seg_bargain"));
    }

    @Test
    @DisplayName("GDPR / CCPA 隐私选择退出与不可逆墓碑安全擦除测试")
    void testCustomerConsentAndTombstonePurge() {
        CustomerConsentManager consent = new CustomerConsentManager();

        String userId = "cust_vip_99";
        Set<String> deviceIds = Set.of("idfa_abc", "cookie_xyz");

        // 默认状态允许合规使用
        assertTrue(consent.isAllowedForTargeting(userId));

        // 场景 1: 用户行使选择退出权 (Opt-out)
        consent.setConsent(userId, false);
        assertFalse(consent.isAllowedForTargeting(userId), "Opt-out 后应被立即禁止定向");

        // 重新同意
        consent.setConsent(userId, true);
        assertTrue(consent.isAllowedForTargeting(userId));

        // 场景 2: 用户行使被遗忘权 (GDPR Article 17 Purge)
        consent.purgeCustomer(userId, deviceIds, "User request under GDPR Art.17");

        assertFalse(consent.isAllowedForTargeting(userId));
        assertTrue(consent.isPurged(userId));
        assertTrue(consent.isPurged("idfa_abc"));
        assertTrue(consent.isPurged("cookie_xyz"));

        // 尝试再次赋权应无效（墓碑不可篡改与不可复活）
        consent.setConsent(userId, true);
        assertFalse(consent.isAllowedForTargeting(userId));
        assertTrue(consent.totalPurgedTombstones() >= 3);
    }

    @Test
    @DisplayName("身份图谱解绑与局部 BFS 连通分量分裂测试 (Unlink & Split via local BFS)")
    void testIdentityUnlinkAndLocalBfsSplitting() {
        IdentityGraphEngine engine = new IdentityGraphEngine();

        // 构建拓扑：phone_1 连 cookie_1，phone_1 连 email_1
        // (email_1 -- phone_1 -- cookie_1)
        engine.link("email_1", IdentityGraphService.IdentifierType.EMAIL, "phone_1", IdentityGraphService.IdentifierType.PHONE, 1.0);
        engine.link("phone_1", IdentityGraphService.IdentifierType.PHONE, "cookie_1", IdentityGraphService.IdentifierType.DEVICE_ID, 0.95);

        Set<String> clusterBefore = engine.getCluster("cookie_1");
        assertEquals(3, clusterBefore.size());
        assertTrue(clusterBefore.containsAll(List.of("email_1", "phone_1", "cookie_1")));

        // 场景 1: 解绑 cookie_1 与 phone_1 (设备换绑/Cookie 过期解绑)
        boolean unlinked = engine.unlink("cookie_1", "phone_1");
        assertTrue(unlinked);

        // 局部 BFS 探测发现无备用路径，图谱发生分裂：
        // cluster 1: {cookie_1}
        // cluster 2: {email_1, phone_1}
        Set<String> clusterCookie = engine.getCluster("cookie_1");
        assertEquals(1, clusterCookie.size());
        assertTrue(clusterCookie.contains("cookie_1"));

        Set<String> clusterPhone = engine.getCluster("phone_1");
        assertEquals(2, clusterPhone.size());
        assertTrue(clusterPhone.containsAll(List.of("email_1", "phone_1")));
        assertFalse(clusterPhone.contains("cookie_1"));

        // 场景 2: 环形拓扑冗余路径解绑 (a -- b, b -- c, c -- a)
        engine.link("node_a", IdentityGraphService.IdentifierType.DEVICE_ID, "node_b", IdentityGraphService.IdentifierType.DEVICE_ID, 1.0);
        engine.link("node_b", IdentityGraphService.IdentifierType.DEVICE_ID, "node_c", IdentityGraphService.IdentifierType.DEVICE_ID, 1.0);
        engine.link("node_c", IdentityGraphService.IdentifierType.DEVICE_ID, "node_a", IdentityGraphService.IdentifierType.DEVICE_ID, 1.0);

        assertEquals(3, engine.getCluster("node_a").size());

        // 解除 node_a 与 node_b 的直连边，但因存在 node_a -> node_c -> node_b 备用路径，图谱不分裂
        engine.unlink("node_a", "node_b");
        Set<String> clusterRing = engine.getCluster("node_a");
        assertEquals(3, clusterRing.size(), "存在环路备用路径时解绑单条边不应导致集群分裂");
        assertTrue(clusterRing.containsAll(List.of("node_a", "node_b", "node_c")));
    }
}
