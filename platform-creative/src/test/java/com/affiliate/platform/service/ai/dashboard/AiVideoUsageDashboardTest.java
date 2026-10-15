package com.affiliate.platform.service.ai.dashboard;

import com.affiliate.platform.domain.ai.*;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardRequest;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardResponse;
import com.affiliate.platform.repository.ai.InMemoryAiGenerateTaskRecordRepository;
import com.affiliate.platform.service.ai.dashboard.impl.AiVideoUsageDashboardServiceImpl;
import com.affiliate.platform.service.billing.impl.CreativeAiBillingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiVideoUsageDashboardTest {

    private InMemoryAiGenerateTaskRecordRepository taskRepo;
    private CreativeAiBillingServiceImpl billingService;
    private AiVideoUsageDashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        taskRepo = new InMemoryAiGenerateTaskRecordRepository();
        billingService = new CreativeAiBillingServiceImpl();
        dashboardService = new AiVideoUsageDashboardServiceImpl(taskRepo, billingService);

        // Seed 3 sample tasks
        AiGenerateTaskRecord t1 = new AiGenerateTaskRecord(
                "id_1", "t_test", "task_001", AiGenerateType.TEXT_TO_VIDEO,
                "doubao_seedance_2_0", "BYTEDANCE_ARK", "rem_001",
                AiTaskStatus.SUCCESS, "Prompt 1", null, null, List.of(),
                null, "raw_1", "oss_1", 10, "SECOND",
                BigDecimal.valueOf(10), BigDecimal.valueOf(1.50), "CALCULATED",
                null, null, "user_alice", Instant.now(), Instant.now()
        );
        AiGenerateTaskRecord t2 = new AiGenerateTaskRecord(
                "id_2", "t_test", "task_002", AiGenerateType.IMAGE_TO_VIDEO,
                "wan_2_1", "DASHSCOPE", "rem_002",
                AiTaskStatus.FAILED, "Prompt 2", "img_start", null, List.of(),
                null, null, null, 5, "SECOND",
                BigDecimal.ZERO, BigDecimal.ZERO, "CANCELLED",
                "UPSTREAM_TIMEOUT", "Server timeout", "user_bob", Instant.now(), Instant.now()
        );
        AiGenerateTaskRecord t3 = new AiGenerateTaskRecord(
                "id_3", "t_test", "task_003", AiGenerateType.TEXT_TO_VIDEO,
                "doubao_seedance_2_0", "BYTEDANCE_ARK", "rem_003",
                AiTaskStatus.PROCESSING, "Prompt 3", null, null, List.of(),
                null, null, null, 15, "SECOND",
                BigDecimal.ZERO, BigDecimal.ZERO, "PENDING",
                null, null, "user_alice", Instant.now(), null
        );

        taskRepo.save(t1);
        taskRepo.save(t2);
        taskRepo.save(t3);

        billingService.confirmComputeDeduction("t_test", "task_001", BigDecimal.valueOf(1.50), "dept_growth", "prj_ecommerce", "doubao_seedance_2_0");
    }

    @Test
    void testOverviewAggregation() {
        AiVideoUsageDashboardResponse resp = dashboardService.getOverview("t_test", AiVideoUsageDashboardRequest.all());
        assertNotNull(resp);

        AiVideoUsageDashboardResponse.Overview overview = resp.overview();
        assertEquals(3, overview.totalCount());
        assertEquals(1, overview.successCount());
        assertEquals(1, overview.failedCount());
        assertEquals(1, overview.processingCount());
        assertEquals(0, overview.canceledCount());
        assertEquals(0, overview.totalCost().compareTo(BigDecimal.valueOf(1.50)));
        assertEquals(10.0, overview.totalOutputDurationSeconds());

        // Trend
        assertFalse(resp.trend().isEmpty());

        // Models
        assertEquals(2, resp.models().size());
        assertEquals("doubao_seedance_2_0", resp.models().get(0).dimensionKey());
        assertEquals(2, resp.models().get(0).totalCount());

        // Users
        assertEquals(2, resp.users().size());

        // Departments
        assertFalse(resp.departments().isEmpty());
        assertEquals("dept_growth", resp.departments().get(0).dimensionKey());
    }

    @Test
    void testDetailsPaginationAndFilter() {
        AiVideoUsageDashboardRequest req = new AiVideoUsageDashboardRequest(
                null, null, List.of("doubao_seedance_2_0"), null, null, "user_alice", 1, 10
        );
        List<AiGenerateTaskRecord> details = dashboardService.getDetails("t_test", req);
        assertEquals(2, details.size());
        assertTrue(details.stream().allMatch(t -> "doubao_seedance_2_0".equals(t.modelCode())));
    }
}
