package com.affiliate.platform.service.quality;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import com.affiliate.platform.domain.quality.MaterialPerformance7D;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.quality.MaterialQualityResult;
import com.affiliate.platform.domain.quality.MaterialSampleStatus;
import com.affiliate.platform.service.quality.impl.MaterialQualityTagRecommendEngineImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("素材质量打标推荐引擎规则单元测试")
class MaterialQualityTagRecommendEngineTest {

    private MaterialQualityTagRecommendEngine engine;
    private MaterialBenchmark7D standardBenchmark;

    @BeforeEach
    void setUp() {
        engine = new MaterialQualityTagRecommendEngineImpl();
        // 标准基准线：
        // 门槛: Cost >= 100, Clicks >= 50, Installs >= 5
        // ROAS: p75=1.80, p25=0.90
        // 消耗: costP50=800.00
        // 成本: cpiAvg=4.00
        // 比率: ctrAvg=0.030 (3%), cvrInstallAvg=0.100 (10%)
        standardBenchmark = new MaterialBenchmark7D(
                "bm_test",
                "tenant_01",
                LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 10, 8),
                LocalDate.of(2026, 10, 14),
                200,
                BigDecimal.valueOf(100.00),
                50L,
                5L,
                BigDecimal.valueOf(1.80),
                BigDecimal.valueOf(0.90),
                BigDecimal.valueOf(800.00),
                BigDecimal.valueOf(4.00),
                BigDecimal.valueOf(0.030),
                BigDecimal.valueOf(0.100),
                Instant.now()
        );
    }

    @Test
    @DisplayName("规则1: 未匹配到投放数据时判定为 UNRATED + UNMATCHED")
    void testUnmatchedAdData() {
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_unmatched",
                "cr_unmatched",
                "New Material",
                null, // 无广告名称
                LocalDate.of(2026, 10, 14),
                BigDecimal.ZERO,
                0,
                0,
                0,
                BigDecimal.ZERO
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.UNRATED, result.qualityLevel());
        assertEquals(MaterialSampleStatus.UNMATCHED, result.sampleStatus());
        assertTrue(result.reasonTags().contains("UNMATCHED_AD_DATA"));
    }

    @Test
    @DisplayName("规则2.1: 消耗不足门槛 (<100) 时判定为 UNRATED + INSUFFICIENT")
    void testInsufficientCost() {
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_small_cost",
                "cr_small_cost",
                "Test Material",
                "AD_cr_small_cost",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(50.00), // < 100
                1000,
                60,
                10,
                BigDecimal.valueOf(150.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.UNRATED, result.qualityLevel());
        assertEquals(MaterialSampleStatus.INSUFFICIENT, result.sampleStatus());
        assertTrue(result.reasonTags().contains("INSUFFICIENT_COST"));
    }

    @Test
    @DisplayName("规则2.2: 点击不足门槛 (<50) 时判定为 UNRATED + INSUFFICIENT")
    void testInsufficientClicks() {
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_small_clicks",
                "cr_small_clicks",
                "Test Material",
                "AD_cr_small_clicks",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(200.00),
                500,
                30, // < 50
                6,
                BigDecimal.valueOf(400.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.UNRATED, result.qualityLevel());
        assertEquals(MaterialSampleStatus.INSUFFICIENT, result.sampleStatus());
        assertTrue(result.reasonTags().contains("INSUFFICIENT_CLICKS"));
    }

    @Test
    @DisplayName("规则2.3: 转化数不足门槛 (<5) 时判定为 UNRATED + INSUFFICIENT")
    void testInsufficientInstalls() {
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_small_installs",
                "cr_small_installs",
                "Test Material",
                "AD_cr_small_installs",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(200.00),
                2000,
                80,
                3, // < 5
                BigDecimal.valueOf(400.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.UNRATED, result.qualityLevel());
        assertEquals(MaterialSampleStatus.INSUFFICIENT, result.sampleStatus());
        assertTrue(result.reasonTags().contains("INSUFFICIENT_INSTALLS"));
    }

    @Test
    @DisplayName("规则3: 命中明星素材 STAR (ROAS>=1.80, CPI<=4.00, Cost>=800.00)")
    void testStarMaterial() {
        // Cost=1000.00 >= 800, Installs=300 -> CPI = 1000/300 = 3.33 <= 4.00
        // ConversionValues=2200.00 -> ROAS = 2200/1000 = 2.20 >= 1.80
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_star_01",
                "cr_star_01",
                "Star Material",
                "AD_cr_star_01",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(1000.00),
                25000,
                1000,
                300,
                BigDecimal.valueOf(2200.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.STAR, result.qualityLevel());
        assertEquals(MaterialSampleStatus.SUFFICIENT, result.sampleStatus());
        assertEquals(BigDecimal.valueOf(95.0), result.score());
        assertTrue(result.reasonTags().contains("HIGH_ROAS"));
        assertTrue(result.reasonTags().contains("LOW_CPI"));
        assertTrue(result.reasonTags().contains("HIGH_SCALE"));
    }

    @Test
    @DisplayName("规则4.1: 命中潜力素材 POTENTIAL (ROAS>=1.80, 但消耗尚未过中位数 Cost<800)")
    void testPotentialMaterialRoomForGrowth() {
        // Cost=400.00 (< 800.00), Installs=100 -> CPI = 4.00
        // ConversionValues=880.00 -> ROAS = 2.20 (>= 1.80)
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_pot_01",
                "cr_pot_01",
                "Potential Material Growth",
                "AD_cr_pot_01",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(400.00),
                10000,
                350,
                100,
                BigDecimal.valueOf(880.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.POTENTIAL, result.qualityLevel());
        assertEquals(BigDecimal.valueOf(80.0), result.score());
        assertTrue(result.reasonTags().contains("HIGH_ROAS"));
        assertTrue(result.reasonTags().contains("ROOM_FOR_GROWTH"));
    }

    @Test
    @DisplayName("规则4.2: 命中潜力素材 POTENTIAL (ROAS>=1.80, 消耗已过800但CPI>4.00且CTR>=0.030)")
    void testPotentialMaterialHighCtr() {
        // Cost=1000.00 (>= 800), Installs=200 -> CPI = 1000/200 = 5.00 (> 4.00, 所以不是 STAR)
        // ConversionValues=2000.00 -> ROAS = 2.00 (>= 1.80)
        // Impressions=10000, Clicks=400 -> CTR = 0.040 (>= 0.030)
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_pot_02",
                "cr_pot_02",
                "Potential Material High CTR",
                "AD_cr_pot_02",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(1000.00),
                10000,
                400,
                200,
                BigDecimal.valueOf(2000.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.POTENTIAL, result.qualityLevel());
        assertTrue(result.reasonTags().contains("HIGH_ROAS"));
        assertTrue(result.reasonTags().contains("HIGH_CTR"));
    }

    @Test
    @DisplayName("规则5: 命中黑洞素材 BLACKHOLE (ROAS<=0.90 且 CPI>4.00)")
    void testBlackholeMaterial() {
        // Cost=1000.00, Installs=150 -> CPI = 6.67 (> 4.00)
        // ConversionValues=600.00 -> ROAS = 0.60 (<= 0.90)
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_blackhole_01",
                "cr_blackhole_01",
                "Blackhole Material",
                "AD_cr_blackhole_01",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(1000.00),
                15000,
                400,
                150,
                BigDecimal.valueOf(600.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.BLACKHOLE, result.qualityLevel());
        assertEquals(BigDecimal.valueOf(15.0), result.score());
        assertTrue(result.reasonTags().contains("LOW_ROAS"));
        assertTrue(result.reasonTags().contains("HIGH_CPI"));
    }

    @Test
    @DisplayName("规则6.1: 命中风险素材 RISK (ROAS<=0.90 但 CPI<=4.00，故不归为黑洞)")
    void testRiskMaterialLowRoas() {
        // Cost=500.00, Installs=200 -> CPI = 2.50 (<= 4.00, 成本控制不差，但回收低)
        // ConversionValues=400.00 -> ROAS = 0.80 (<= 0.90)
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_risk_01",
                "cr_risk_01",
                "Risk Material Low ROAS",
                "AD_cr_risk_01",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(500.00),
                12000,
                300,
                200,
                BigDecimal.valueOf(400.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.RISK, result.qualityLevel());
        assertEquals(BigDecimal.valueOf(35.0), result.score());
        assertTrue(result.reasonTags().contains("LOW_ROAS"));
    }

    @Test
    @DisplayName("规则6.2: 命中风险素材 RISK (ROAS中间正常，但前链路CTR<0.030 且 后链路CVR<0.100)")
    void testRiskMaterialDualWeak() {
        // ROAS: 1200 / 1000 = 1.20 (在 0.90 ~ 1.80 之间)
        // Impressions=40000, Clicks=600 -> CTR = 0.015 (< 0.030)
        // Installs=40 -> CVR = 40/600 = 0.0667 (< 0.100)
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_risk_02",
                "cr_risk_02",
                "Risk Material Dual Weak",
                "AD_cr_risk_02",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(1000.00),
                40000,
                600,
                40,
                BigDecimal.valueOf(1200.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.RISK, result.qualityLevel());
        assertTrue(result.reasonTags().contains("LOW_CTR"));
        assertTrue(result.reasonTags().contains("LOW_CVR"));
    }

    @Test
    @DisplayName("规则7: 兜底判定为主力素材 CORE (样本充足平稳，无突出暴富亦无严重缺陷)")
    void testCoreMaterial() {
        // Cost=1000.00 (>= 800), Installs=280 -> CPI = 3.57 (<= 4.00)
        // ConversionValues=1400.00 -> ROAS = 1.40 (在 0.90 ~ 1.80 之间，不达 STAR 亦非 RISK/BLACKHOLE)
        // Impressions=20000, Clicks=800 -> CTR = 0.040 (>= 0.030)
        // CVR = 280 / 800 = 0.35 (>= 0.100)
        MaterialPerformance7D perf = new MaterialPerformance7D(
                "cr_core_01",
                "cr_core_01",
                "Core Material",
                "AD_cr_core_01",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(1000.00),
                20000,
                800,
                280,
                BigDecimal.valueOf(1400.00)
        );

        MaterialQualityResult result = engine.evaluate(perf, standardBenchmark);

        assertEquals(MaterialQualityLevel.CORE, result.qualityLevel());
        assertEquals(BigDecimal.valueOf(60.0), result.score());
        assertTrue(result.reasonTags().contains("CORE_BACKBONE"));
    }
}
