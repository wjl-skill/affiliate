package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.service.AffiliateAntiFraudEngine;
import com.affiliate.platform.affiliate.service.ConversionAttributionService;
import com.affiliate.platform.affiliate.service.ConversionAttributionService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("业务深化拓展：多模型归因算法与高级多维反欺诈质检测试")
public class AttributionAndAntiFraudDeepeningTest {

    private ConversionAttributionService attributionService;
    private AffiliateAntiFraudEngine antiFraudEngine;

    @BeforeEach
    void setUp() {
        // 使用单测构造函数注入（无数据库依赖快速验证算法逻辑）
        attributionService = new ConversionAttributionService(null, null, null, null, null);
        antiFraudEngine = new AffiliateAntiFraudEngine(null, null);
    }

    @Test
    @DisplayName("测试数据驱动归因 (Data-Driven MTA)：综合时间衰减、交互深度与边际递减且全额平账")
    void testDataDrivenAttribution() {
        Instant now = Instant.now();
        List<TouchPoint> touchPoints = List.of(
                new TouchPoint("tp1", "u1", "s1", TouchPointType.IMPRESSION, "aff_search", "off_1", "c1", "google", "cpc", "camp1", now.minus(10, ChronoUnit.DAYS)),
                new TouchPoint("tp2", "u1", "s2", TouchPointType.VIEW, "aff_display", "off_1", "c2", "fb", "display", "camp1", now.minus(5, ChronoUnit.DAYS)),
                new TouchPoint("tp3", "u1", "s3", TouchPointType.CLICK, "aff_search", "off_1", "c3", "google", "cpc", "camp1", now.minus(2, ChronoUnit.DAYS)),
                new TouchPoint("tp4", "u1", "s4", TouchPointType.CLICK, "aff_influencer", "off_1", "c4", "yt", "social", "camp1", now.minus(1, ChronoUnit.HOURS))
        );

        BigDecimal conversionValue = new BigDecimal("250.00");
        List<AttributionCredit> credits = attributionService.dataDrivenAttribution(touchPoints, conversionValue);

        assertNotNull(credits);
        assertEquals(4, credits.size());

        // 验证全量分成求和严格等于总转化金额 (无损平账)
        BigDecimal totalCredited = credits.stream()
                .map(AttributionCredit::creditedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(conversionValue, totalCredited, "数据驱动归因分配总金额必须严格等于转化总金额");

        // 验证末次转化促成触点获得最高权重 (离转化最近且为 CLICK)
        AttributionCredit lastCredit = credits.get(3);
        assertEquals("aff_influencer", lastCredit.affiliateId());
        assertTrue(lastCredit.weight().compareTo(credits.get(0).weight()) > 0);
    }

    @Test
    @DisplayName("测试多模型归因对比分析：一键横向比对全部6种归因模型")
    void testCompareAttributionModels() {
        Instant now = Instant.now();
        List<TouchPoint> touchPoints = List.of(
                new TouchPoint("tp1", "u1", "s1", TouchPointType.CLICK, "aff_first", "off_1", "c1", "seo", "organic", "camp1", now.minus(3, ChronoUnit.DAYS)),
                new TouchPoint("tp2", "u1", "s2", TouchPointType.CLICK, "aff_middle", "off_1", "c2", "email", "newsletter", "camp1", now.minus(1, ChronoUnit.DAYS)),
                new TouchPoint("tp3", "u1", "s3", TouchPointType.CLICK, "aff_last", "off_1", "c3", "affiliate", "cpa", "camp1", now.minus(10, ChronoUnit.MINUTES))
        );

        BigDecimal conversionValue = new BigDecimal("100.00");
        Map<AttributionModel, List<AttributionCredit>> comparison =
                attributionService.compareAttributionModels(touchPoints, conversionValue);

        assertNotNull(comparison);
        assertTrue(comparison.containsKey(AttributionModel.LAST_CLICK));
        assertTrue(comparison.containsKey(AttributionModel.FIRST_CLICK));
        assertTrue(comparison.containsKey(AttributionModel.LINEAR));
        assertTrue(comparison.containsKey(AttributionModel.TIME_DECAY));
        assertTrue(comparison.containsKey(AttributionModel.POSITION_BASED));
        assertTrue(comparison.containsKey(AttributionModel.DATA_DRIVEN));

        // 1. Last Click 100% 归于末次
        List<AttributionCredit> lastCredits = comparison.get(AttributionModel.LAST_CLICK);
        assertEquals(1, lastCredits.size());
        assertEquals("aff_last", lastCredits.get(0).affiliateId());
        assertEquals(new BigDecimal("100.00"), lastCredits.get(0).creditedValue());

        // 2. First Click 100% 归于首次
        List<AttributionCredit> firstCredits = comparison.get(AttributionModel.FIRST_CLICK);
        assertEquals(1, firstCredits.size());
        assertEquals("aff_first", firstCredits.get(0).affiliateId());
        assertEquals(new BigDecimal("100.00"), firstCredits.get(0).creditedValue());

        // 3. Position Based (U-Shape 40-20-40)
        List<AttributionCredit> posCredits = comparison.get(AttributionModel.POSITION_BASED);
        assertEquals(3, posCredits.size());
        assertEquals(new BigDecimal("40.00"), posCredits.get(0).creditedValue());
        assertEquals(new BigDecimal("20.00"), posCredits.get(1).creditedValue());
        assertEquals(new BigDecimal("40.00"), posCredits.get(2).creditedValue());
    }

    @Test
    @DisplayName("测试高级反欺诈：跨国地理漂移检测 (Geo Drift)")
    void testGeoLocationDriftDetection() {
        Instant now = Instant.now();
        Instant clickTime = now.minusSeconds(15); // 15秒极速跨国

        ClickSession session = new ClickSession(
                "c_drift_01", "public", "off_101", "aff_99",
                "sub1", null, null, null, null,
                "192.168.1.100", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "US", 2, clickTime, clickTime.plusSeconds(3600)
        );

        // 转化在 CN 且 CTIT 仅 15 秒
        AffiliateAntiFraudEngine.FraudInspectionResult result = antiFraudEngine.inspectConversion(
                session, "tx_drift_" + UUID.randomUUID(), now,
                "10.0.0.1", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "CN", 2
        );

        assertNotNull(result);
        assertTrue(result.riskReasons().contains("GEO_LOCATION_DRIFT_SUSPECTED"), "应成功识别跨国超音速地理漂移");
        assertTrue(result.riskScore() >= 55);
        assertTrue(antiFraudEngine.getCumulativeCounters().get("geoAnomalyCount") > 0);
    }

    @Test
    @DisplayName("测试高级反欺诈：设备环境突变与系统平台不匹配 (Device/OS Mismatch)")
    void testDeviceAndPlatformMismatch() {
        Instant now = Instant.now();
        Instant clickTime = now.minusSeconds(60);

        // 点击来自移动端 iPhone
        ClickSession session = new ClickSession(
                "c_device_01", "public", "off_101", "aff_99",
                "sub1", null, null, null, null,
                "192.168.1.100", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)",
                "US", 1, clickTime, clickTime.plusSeconds(3600)
        );

        // 转化上报却来自桌面端 Windows PC (DeviceType 1 -> 2, UA 从 iOS 突变为 Windows)
        AffiliateAntiFraudEngine.FraudInspectionResult result = antiFraudEngine.inspectConversion(
                session, "tx_mismatch_" + UUID.randomUUID(), now,
                "192.168.1.100", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "US", 2
        );

        assertNotNull(result);
        assertTrue(result.riskReasons().contains("DEVICE_ENVIRONMENT_MISMATCH"), "应识别设备类型突变");
        assertTrue(result.riskReasons().contains("PLATFORM_OS_MISMATCH"), "应识别系统平台突变");
        assertTrue(result.riskScore() >= 70, "双重设备环境不匹配应触发高危作弊判定");
        assertEquals(Conversion.Status.FRAUD_SUSPECTED, result.recommendedStatus());
        assertTrue(antiFraudEngine.getCumulativeCounters().get("deviceAnomalyCount") > 0);
    }

    @Test
    @DisplayName("测试高级反欺诈：单 IP 分钟级转化突发泛滥风控拦截")
    void testIpConversionBurstFlood() {
        Instant now = Instant.now();
        Instant clickTime = now.minusSeconds(30);

        ClickSession session = new ClickSession(
                "c_burst_01", "public", "off_101", "aff_99",
                "sub1", null, null, null, null,
                "203.0.113.199", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
                "US", 2, clickTime, clickTime.plusSeconds(3600)
        );

        // 同一 IP 连续上报 10 次正常
        for (int i = 0; i < 10; i++) {
            antiFraudEngine.inspectConversion(
                    session, "tx_ok_" + i, now,
                    "203.0.113.199", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", "US", 2
            );
        }

        // 第 11 次应触发 IP_CONVERSION_BURST_FLOOD
        AffiliateAntiFraudEngine.FraudInspectionResult result = antiFraudEngine.inspectConversion(
                session, "tx_burst_flood", now,
                "203.0.113.199", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", "US", 2
        );

        assertTrue(result.riskReasons().contains("IP_CONVERSION_BURST_FLOOD"), "第11次同一IP转化应触发频率泛滥拦截");
    }
}
