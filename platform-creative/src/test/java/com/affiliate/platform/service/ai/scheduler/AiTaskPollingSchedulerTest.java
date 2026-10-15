package com.affiliate.platform.service.ai.scheduler;

import com.affiliate.platform.domain.ai.*;
import com.affiliate.platform.repository.ai.InMemoryAiGenerateTaskRecordRepository;
import com.affiliate.platform.service.ai.AiVideoGenerationService;
import com.affiliate.platform.service.ai.generator.VideoGeneratorFactory;
import com.affiliate.platform.service.ai.meta.impl.AiVideoModelMetaServiceImpl;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import com.affiliate.platform.service.billing.impl.CreativeAiBillingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiTaskPollingSchedulerTest {

    private InMemoryAiGenerateTaskRecordRepository taskRepo;
    private CreativeAiBillingServiceImpl billingService;
    private AiVideoGenerationService generationService;
    private AiTaskPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        taskRepo = new InMemoryAiGenerateTaskRecordRepository();
        billingService = new CreativeAiBillingServiceImpl();
        generationService = new AiVideoGenerationService(
                new AiVideoModelMetaServiceImpl(),
                new VideoGeneratorFactory(List.of()),
                new VideoProviderFactory(List.of()),
                taskRepo,
                e -> {},
                billingService
        );
        scheduler = new AiTaskPollingScheduler(generationService, taskRepo, billingService);
    }

    @Test
    void testPollPendingAiTasksEmpty() {
        int count = scheduler.pollPendingAiTasks();
        assertEquals(0, count);
    }

    @Test
    void testCleanupStaleProcessingTask() {
        // Create a task that was created 3 hours ago in PROCESSING status
        Instant threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS);
        AiGenerateTaskRecord staleTask = new AiGenerateTaskRecord(
                "stale_1", "t_stale", "task_stale_001", AiGenerateType.TEXT_TO_VIDEO,
                "doubao_seedance_2_0", "BYTEDANCE_ARK", "rem_stale",
                AiTaskStatus.PROCESSING, "Prompt stale", null, null, List.of(),
                null, null, null, 10, "SECOND",
                BigDecimal.ZERO, BigDecimal.valueOf(1.50), "PENDING",
                null, null, "user_stale", threeHoursAgo, null
        );
        taskRepo.save(staleTask);
        billingService.reserveComputeFund("t_stale", "task_stale_001", BigDecimal.valueOf(1.50));
        assertTrue(billingService.hasActiveHold("task_stale_001"));

        int cleaned = scheduler.cleanupStaleTasks();
        assertEquals(1, cleaned);

        // Verify task transitioned to FAILED and hold released
        AiGenerateTaskRecord updated = taskRepo.findByTaskNo("t_stale", "task_stale_001").orElseThrow();
        assertEquals(AiTaskStatus.FAILED, updated.status());
        assertEquals("TASK_TIMEOUT_EXCEEDED", updated.errorCode());
        assertFalse(billingService.hasActiveHold("task_stale_001"));
    }

    @Test
    void testCleanupExpiredFetchedTask() {
        // Create a task that was created 6 days ago in SUCCESS_FETCHED status
        Instant sixDaysAgo = Instant.now().minus(6, ChronoUnit.DAYS);
        AiGenerateTaskRecord fetchedTask = new AiGenerateTaskRecord(
                "fetch_1", "t_fetch", "task_fetch_001", AiGenerateType.TEXT_TO_VIDEO,
                "doubao_seedance_2_0", "BYTEDANCE_ARK", "rem_fetch",
                AiTaskStatus.SUCCESS_FETCHED, "Prompt fetch", null, null, List.of(),
                null, "raw_tmp_url", null, 10, "SECOND",
                BigDecimal.ZERO, BigDecimal.ZERO, "PENDING",
                null, null, "user_fetch", sixDaysAgo, null
        );
        taskRepo.save(fetchedTask);

        int cleaned = scheduler.cleanupStaleTasks();
        assertEquals(1, cleaned);

        AiGenerateTaskRecord updated = taskRepo.findByTaskNo("t_fetch", "task_fetch_001").orElseThrow();
        assertEquals(AiTaskStatus.DISCARDED, updated.status());
    }
}
