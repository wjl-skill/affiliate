package com.affiliate.platform.web.quality;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.quality.MaterialQualityResult;
import com.affiliate.platform.repository.quality.InMemoryMaterialBenchmarkRepository;
import com.affiliate.platform.repository.quality.InMemoryMaterialQualityHistoryRepository;
import com.affiliate.platform.service.quality.MaterialQualityService;
import com.affiliate.platform.service.quality.impl.MaterialQualityTagRecommendEngineImpl;
import com.affiliate.platform.web.quality.dto.BenchmarkSaveRequest;
import com.affiliate.platform.web.quality.dto.MaterialEvaluateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("素材质量打标 REST 控制器端点测试")
class MaterialQualityControllerTest {

    private MaterialQualityController controller;
    private MaterialQualityService service;

    @BeforeEach
    void setUp() {
        var benchmarkRepo = new InMemoryMaterialBenchmarkRepository();
        var historyRepo = new InMemoryMaterialQualityHistoryRepository();
        var engine = new MaterialQualityTagRecommendEngineImpl();
        service = new MaterialQualityService(benchmarkRepo, historyRepo, engine, event -> {});
        var jobService = new com.affiliate.platform.service.quality.impl.MaterialQualityTagJobServiceImpl(service);
        controller = new MaterialQualityController(service, jobService);
    }

    @Test
    @DisplayName("测试评级接口 POST /api/v1/creative/quality/evaluate")
    void testEvaluateEndpoint() {
        MaterialEvaluateRequest req = new MaterialEvaluateRequest(
                "cr_ctrl_01",
                "Sample Video 01",
                "AD_Sample_01",
                LocalDate.of(2026, 10, 14),
                BigDecimal.valueOf(1500.00),
                35000,
                1500,
                400,
                BigDecimal.valueOf(3300.00) // ROAS = 2.20
        );

        MaterialQualityResult result = controller.evaluate(req);

        assertNotNull(result);
        assertEquals("cr_ctrl_01", result.creativeId());
        assertEquals(MaterialQualityLevel.STAR, result.qualityLevel());

        // 验证 recommend 端点检索
        MaterialQualityResult rec = controller.recommend("cr_ctrl_01");
        assertEquals(result.id(), rec.id());
        assertEquals(MaterialQualityLevel.STAR, rec.qualityLevel());
    }

    @Test
    @DisplayName("测试大盘基准线同步接口 POST /api/v1/creative/quality/benchmark")
    void testBenchmarkEndpoint() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        BenchmarkSaveRequest req = new BenchmarkSaveRequest(
                date,
                date.minusDays(6),
                date,
                300,
                BigDecimal.valueOf(100.00),
                50L,
                5L,
                BigDecimal.valueOf(1.90),
                BigDecimal.valueOf(0.85),
                BigDecimal.valueOf(900.00),
                BigDecimal.valueOf(3.80),
                BigDecimal.valueOf(0.032),
                BigDecimal.valueOf(0.110)
        );

        MaterialBenchmark7D saved = controller.saveBenchmark(req);

        assertNotNull(saved);
        assertEquals(date, saved.statDate());
        assertEquals(BigDecimal.valueOf(1.90), saved.roasP75());

        MaterialBenchmark7D latest = controller.getLatestBenchmark();
        assertEquals(saved.id(), latest.id());
    }

    @Test
    @DisplayName("测试历史记录与列表检索接口 GET /history, GET /list")
    void testHistoryAndListEndpoints() {
        LocalDate today = LocalDate.of(2026, 10, 14);

        // 提交评级
        MaterialEvaluateRequest req = new MaterialEvaluateRequest(
                "cr_ctrl_02",
                "Promo Banner",
                "AD_Banner",
                today,
                BigDecimal.valueOf(200.00),
                10000,
                300,
                80,
                BigDecimal.valueOf(500.00)
        );
        controller.evaluate(req);

        List<MaterialQualityResult> history = controller.getHistory("cr_ctrl_02", 5);
        assertEquals(1, history.size());
        assertEquals("cr_ctrl_02", history.getFirst().creativeId());

        List<MaterialQualityResult> list = controller.listByLevel(history.getFirst().qualityLevel(), today);
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(r -> "cr_ctrl_02".equals(r.creativeId())));
    }

    @Test
    @DisplayName("测试批量刷新与定时巡检任务接口")
    void testJobEndpoints() {
        var summary = controller.executeDailyRefresh();
        assertNotNull(summary);
        assertTrue(summary.totalProcessed() >= 2);
        assertTrue(summary.starCount() >= 1);

        var batchSummary = controller.batchRefresh(LocalDate.now(), List.of());
        assertNotNull(batchSummary);
        assertEquals(0, batchSummary.totalProcessed());
    }
}
