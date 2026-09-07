package com.affiliate.platform.dsp;

import com.affiliate.platform.repository.InMemoryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CampaignServiceTest {

    @Test
    void activatesAndMatchesByTargeting() {
        CampaignService service = new CampaignService(new InMemoryRepository<>(Campaign::id), event -> {});
        Campaign created = service.create(new Campaign(
                null,
                "adv-1",
                "summer",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                new BigDecimal("100"),
                new BigDecimal("2"),
                Set.of("example.com"),
                Set.of(1),
                Campaign.Status.DRAFT,
                null
        ));
        service.setActive(created.id(), true);
        assertEquals(1, service.match("EXAMPLE.COM", 1, LocalDate.now()).size());
        assertTrue(service.match("other.example", 1, LocalDate.now()).isEmpty());

        // 测试 TrafficContext 匹配
        TrafficContext ctx = new TrafficContext("example.com", 1, LocalDate.now(), 14, DayOfWeek.WEDNESDAY, "US", Set.of());
        assertEquals(1, service.match(ctx).size());
    }

    @Test
    void daypartingScheduleFiltering() {
        Dayparting workHours = Dayparting.businessHours();
        // 周一 10点 允许
        assertTrue(workHours.allows(DayOfWeek.MONDAY, 10));
        // 周一 22点 拒绝
        assertFalse(workHours.allows(DayOfWeek.MONDAY, 22));
        // 周日 14点 拒绝
        assertFalse(workHours.allows(DayOfWeek.SUNDAY, 14));

        Dayparting all = Dayparting.allHours();
        assertTrue(all.allows(DayOfWeek.SUNDAY, 3));
    }

    @Test
    void biddingStrategyOcpmCalculationAndBidShading() {
        // oCPM 模式：targetCpa = 50.00, maxBid = 15.00, bidShading = 0.85
        BiddingStrategy ocpm = new BiddingStrategy(
                BiddingStrategy.BiddingType.OCPM,
                new BigDecimal("50.00"),
                new BigDecimal("15.00"),
                0.85
        );

        // pCTR = 0.02 (2%), pCVR = 0.10 (10%)
        // rawEcpm = 0.02 * 0.10 * 50.00 * 1000 = 100.00
        // capped = min(100.00, 15.00) = 15.00
        // finalBid = 15.00 * 0.85 = 12.7500
        BigDecimal ecpm = ocpm.calculateEcpm(0.02, 0.10);
        assertEquals(new BigDecimal("12.7500"), ecpm);

        // CPC 模式：targetCpc = 1.20, maxBid = 10.00, bidShading = 1.0
        BiddingStrategy cpc = new BiddingStrategy(
                BiddingStrategy.BiddingType.CPC,
                new BigDecimal("1.20"),
                new BigDecimal("10.00"),
                1.0
        );
        // pCTR = 0.01 (1%) -> eCPM = 0.01 * 1.20 * 1000 = 12.00 -> capped to 10.00
        assertEquals(new BigDecimal("10.0000"), cpc.calculateEcpm(0.01, 0.0));
    }

    @Test
    void adGroupMatchingWithGeosAndSegments() {
        BiddingStrategy strategy = new BiddingStrategy(BiddingStrategy.BiddingType.FIXED_CPM, new BigDecimal("2.50"), new BigDecimal("5.00"), 1.0);
        AdGroup adGroup = new AdGroup(
                "ag-1",
                "camp-1",
                "US-Sports-Segment",
                strategy,
                Set.of("US", "CA"),
                Set.of(1, 2),
                Set.of("espn.com"),
                Dayparting.allHours(),
                Set.of("seg_sports"),
                Set.of("seg_blacklist"),
                AdGroup.Status.ACTIVE,
                null
        );

        // 匹配成功流量
        TrafficContext matchCtx = new TrafficContext("espn.com", 1, LocalDate.now(), 15, DayOfWeek.FRIDAY, "US", Set.of("seg_sports", "seg_vip"));
        assertTrue(adGroup.matches(matchCtx));

        // 国家不匹配
        TrafficContext wrongCountry = new TrafficContext("espn.com", 1, LocalDate.now(), 15, DayOfWeek.FRIDAY, "FR", Set.of("seg_sports"));
        assertFalse(adGroup.matches(wrongCountry));

        // 包含排除黑名单人群
        TrafficContext blacklisted = new TrafficContext("espn.com", 1, LocalDate.now(), 15, DayOfWeek.FRIDAY, "US", Set.of("seg_sports", "seg_blacklist"));
        assertFalse(adGroup.matches(blacklisted));
    }
}
