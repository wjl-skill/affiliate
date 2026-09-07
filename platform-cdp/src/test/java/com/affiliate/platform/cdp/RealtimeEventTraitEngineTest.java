package com.affiliate.platform.cdp;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeEventTraitEngineTest {

    @Test
    void evaluatesWhalePurchaserAndDeviceTraits() {
        CustomerTimelineService timelineService = new CustomerTimelineService();
        RealtimeEventTraitEngine engine = new RealtimeEventTraitEngine(null, timelineService);

        Instant now = Instant.now();
        String user = "user_whale_1";

        // 模拟大额支付 $600，发生在移动端，电子类目
        RealtimeEventTraitEngine.UserBehaviorSnapshot snap = engine.ingestEvent(
                user,
                RealtimeEventTraitEngine.EventType.PURCHASE,
                new BigDecimal("600.00"),
                "Electronics",
                "MOBILE",
                now
        );

        assertEquals(new BigDecimal("600.00"), snap.totalSpend());
        assertEquals(1, snap.purchaseCount());
        assertEquals("electronics", snap.preferredCategory());
        assertEquals("MOBILE", snap.preferredDevice());

        Set<String> traits = snap.dynamicTraits();
        assertTrue(traits.contains("whale_purchaser"), "应当打上大额购买者标签");
        assertTrue(traits.contains("mobile_native"), "应当打上移动端偏好标签");
        assertTrue(traits.contains("pref_electronics"), "应当打上品类偏好标签");
    }

    @Test
    void evaluatesBargainHunterAndWindowShopper() {
        RealtimeEventTraitEngine engine = new RealtimeEventTraitEngine();
        Instant now = Instant.now();

        // 1. 橱窗型访客：点击 8 次，0 购买
        String windowUser = "user_shopper";
        for (int i = 0; i < 8; i++) {
            engine.ingestEvent(windowUser, RealtimeEventTraitEngine.EventType.CLICK, null, "Fashion", "DESKTOP", now);
        }
        var shopperSnap = engine.getSnapshot(windowUser).orElseThrow();
        assertEquals(8, shopperSnap.clickCount());
        assertTrue(shopperSnap.dynamicTraits().contains("window_shopper"));

        // 2. 价格敏感型：购买 2 次，总金额 $25.00 (客单价 $12.50 < $20)
        String bargainUser = "user_bargain";
        engine.ingestEvent(bargainUser, RealtimeEventTraitEngine.EventType.PURCHASE, new BigDecimal("10.00"), "Groceries", "MOBILE", now);
        engine.ingestEvent(bargainUser, RealtimeEventTraitEngine.EventType.PURCHASE, new BigDecimal("15.00"), "Groceries", "MOBILE", now);

        var bargainSnap = engine.getSnapshot(bargainUser).orElseThrow();
        assertEquals(2, bargainSnap.purchaseCount());
        assertEquals(new BigDecimal("25.00"), bargainSnap.totalSpend());
        assertTrue(bargainSnap.dynamicTraits().contains("bargain_hunter"));
    }

    @Test
    void evaluates7dActiveAndChurnRisk() {
        RealtimeEventTraitEngine engine = new RealtimeEventTraitEngine();
        Instant now = Instant.now();
        String activeUser = "user_active";

        // 3次触点
        engine.ingestEvent(activeUser, RealtimeEventTraitEngine.EventType.CLICK, null, null, null, now.minus(2, ChronoUnit.DAYS));
        engine.ingestEvent(activeUser, RealtimeEventTraitEngine.EventType.PAGEVIEW, null, null, null, now.minus(1, ChronoUnit.DAYS));
        var snap = engine.ingestEvent(activeUser, RealtimeEventTraitEngine.EventType.CLICK, null, null, null, now);

        assertEquals(3, snap.events7dCount());
        assertTrue(snap.dynamicTraits().contains("active_7d"));

        // 离线 15 天前活动过 -> 流失风险
        RealtimeEventTraitEngine.InternalState churnState = new RealtimeEventTraitEngine.InternalState("user_churn");
        churnState.lastSeenAt = now.minus(15, ChronoUnit.DAYS);
        churnState.purchaseCount = 1;
        Set<String> traits = engine.evaluateTraits(churnState, now);
        assertTrue(traits.contains("churn_risk"));
    }
}
