package com.affiliate.platform.cdp;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CdpGraphAndRfmTest {

    @Test
    void identityGraphConfidenceLinking() {
        IdentityGraphService graph = new IdentityGraphService();

        // 确定性手机号 (置信度 1.0) -> 大于门槛 0.80 -> 关联成功
        IdentityGraphService.IdentityNode phoneNode = new IdentityGraphService.IdentityNode("+1234567890", IdentityGraphService.IdentifierType.PHONE, 1.0);
        assertTrue(graph.linkIdentifier("cust_101", phoneNode, 0.80));
        assertTrue(graph.contains("cust_101", "+1234567890"));

        // 概率性低置信度指纹 (置信度 0.60) -> 低于门槛 0.80 -> 拒绝关联
        IdentityGraphService.IdentityNode weakFingerprint = new IdentityGraphService.IdentityNode("fp_weak_99", IdentityGraphService.IdentifierType.PROBABILISTIC_FINGERPRINT, 0.60);
        assertFalse(graph.linkIdentifier("cust_101", weakFingerprint, 0.80));
        assertFalse(graph.contains("cust_101", "fp_weak_99"));
    }

    @Test
    void customerTimelineAndRfmScoring() {
        CustomerTimelineService timelineService = new CustomerTimelineService();
        RfmScoringService rfmService = new RfmScoringService();

        Instant now = Instant.now();

        // 记录用户事件流
        timelineService.recordEvent("cust_202", CustomerTimelineService.EventType.IMPRESSION, now.minus(20, ChronoUnit.DAYS), Map.of("campaign", "c1"));
        timelineService.recordEvent("cust_202", CustomerTimelineService.EventType.AD_CLICK, now.minus(20, ChronoUnit.DAYS), Map.of("campaign", "c1"));

        // 记录 3 次购买，累计金额 600 元，最近购买发生在 5 天前
        timelineService.recordEvent("cust_202", CustomerTimelineService.EventType.PURCHASE, now.minus(18, ChronoUnit.DAYS), Map.of("amount", "150.00"));
        timelineService.recordEvent("cust_202", CustomerTimelineService.EventType.PURCHASE, now.minus(10, ChronoUnit.DAYS), Map.of("amount", "200.00"));
        timelineService.recordEvent("cust_202", CustomerTimelineService.EventType.PURCHASE, now.minus(5, ChronoUnit.DAYS), Map.of("amount", "250.00"));

        List<CustomerTimelineService.CustomerEvent> events = timelineService.getTimeline("cust_202");
        assertEquals(5, events.size());
        // 最新的事件排在最前面
        assertEquals(now.minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS), events.get(0).timestamp().truncatedTo(ChronoUnit.SECONDS));

        // 计算 RFM 分值 -> R=5天, F=3次, M=600.00 -> 满足 CHAMPION 核心冠军客户
        RfmScoringService.RfmScore score = rfmService.calculate(events, now);
        assertEquals(5, score.recencyDays());
        assertEquals(3, score.frequency());
        assertEquals(new BigDecimal("600.00"), score.monetary());
        assertEquals(RfmScoringService.CustomerTier.CHAMPION, score.tier());
    }
}
