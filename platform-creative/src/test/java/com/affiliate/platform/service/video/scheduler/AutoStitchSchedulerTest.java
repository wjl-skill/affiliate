package com.affiliate.platform.service.video.scheduler;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.AutoStitchConfig;
import com.affiliate.platform.domain.video.AutoStitchStatus;
import com.affiliate.platform.repository.video.InMemoryAutoStitchConfigRepository;
import com.affiliate.platform.repository.video.InMemoryVideoProcessTaskRepository;
import com.affiliate.platform.service.video.AutoStitchService;
import com.affiliate.platform.service.video.impl.AutoStitchServiceImpl;
import com.affiliate.platform.service.video.impl.VideoProcessServiceImpl;
import com.affiliate.platform.service.video.operator.*;
import com.affiliate.platform.service.video.probe.impl.MediaProbeServiceImpl;
import com.affiliate.platform.service.video.storage.WorkDirManager;
import com.affiliate.platform.web.video.dto.AutoStitchCreateRequest;
import com.affiliate.platform.web.video.dto.AutoStitchTriggerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoStitchScheduler Tests")
class AutoStitchSchedulerTest {

    private InMemoryAutoStitchConfigRepository configRepo;
    private InMemoryVideoProcessTaskRepository taskRepo;
    private AutoStitchService autoStitchService;
    private VideoProcessServiceImpl videoProcessService;
    private AutoStitchScheduler scheduler;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        configRepo = new InMemoryAutoStitchConfigRepository();
        taskRepo = new InMemoryVideoProcessTaskRepository();
        autoStitchService = new AutoStitchServiceImpl();

        WorkDirManager workDirManager = new WorkDirManager();
        ReflectionTestUtils.setField(workDirManager, "baseWorkspaceDir", tempDir.toAbsolutePath().toString());
        workDirManager.setHighWatermarkThreshold(80.0);
        workDirManager.setSimulatedSpace(10_000_000_000L, 7_000_000_000L);

        List<IVideoOperator> operators = List.of(
                new WatermarkRemovalOperator(),
                new SubtitleProcessorOperator(),
                new IntroSplicingOperator(),
                new CtaOverlayOperator(),
                new TrimOperator()
        );
        VideoOperatorFactory factory = new VideoOperatorFactory(operators);
        MediaProbeServiceImpl probeService = new MediaProbeServiceImpl();

        videoProcessService = new VideoProcessServiceImpl(
                taskRepo, factory, probeService, workDirManager, e -> {}, Runnable::run
        );

        scheduler = new AutoStitchScheduler(configRepo, autoStitchService, videoProcessService);
    }

    @Test
    @DisplayName("Should create config and trigger Cartesian auto stitch tasks")
    void shouldCreateAndTriggerBatchStitch() {
        AutoStitchCreateRequest req = new AutoStitchCreateRequest(
                "Batch_Test_Promo",
                List.of("hook1.mp4", "hook2.mp4"),
                List.of("body1.mp4", "body2.mp4", "body3.mp4"),
                "9:16",
                AlignMode.LETTERBOX,
                Map.of("ctaText", "点击免费玩"),
                null
        );

        AutoStitchConfig config = scheduler.createConfig("tenant_abc", req);
        assertNotNull(config.id());
        assertEquals(AutoStitchStatus.PENDING, config.status());

        // Trigger batch execution: 2 hooks x 3 bodies = 6 tasks
        AutoStitchTriggerResult result = scheduler.triggerBatchStitch("tenant_abc", config.id());
        assertEquals(6, result.generatedTaskCount());
        assertEquals(6, result.taskNos().size());

        // Verify config status transitioned to EXECUTED
        AutoStitchConfig updated = scheduler.findConfigById("tenant_abc", config.id()).orElseThrow();
        assertEquals(AutoStitchStatus.EXECUTED, updated.status());

        // Verify idempotency on second trigger
        AutoStitchTriggerResult secondResult = scheduler.triggerBatchStitch("tenant_abc", config.id());
        assertEquals(0, secondResult.generatedTaskCount());
    }

    @Test
    @DisplayName("Scheduled polling should auto-trigger pending configs whose time is reached")
    void shouldPollAndTriggerScheduledConfigs() {
        // Scheduled in the past -> should trigger
        AutoStitchCreateRequest pastReq = new AutoStitchCreateRequest(
                "Past_Config",
                List.of("h1.mp4"),
                List.of("b1.mp4", "b2.mp4"),
                "9:16",
                AlignMode.LETTERBOX,
                Map.of(),
                Instant.now().minus(5, ChronoUnit.MINUTES)
        );
        AutoStitchConfig pastConfig = scheduler.createConfig("tenant_abc", pastReq);

        // Scheduled in the future -> should NOT trigger
        AutoStitchCreateRequest futureReq = new AutoStitchCreateRequest(
                "Future_Config",
                List.of("h2.mp4"),
                List.of("b3.mp4"),
                "9:16",
                AlignMode.LETTERBOX,
                Map.of(),
                Instant.now().plus(60, ChronoUnit.MINUTES)
        );
        AutoStitchConfig futureConfig = scheduler.createConfig("tenant_abc", futureReq);

        int triggered = scheduler.pollAndTriggerScheduledConfigs();
        assertEquals(1, triggered);

        assertEquals(AutoStitchStatus.EXECUTED, scheduler.findConfigById("tenant_abc", pastConfig.id()).orElseThrow().status());
        assertEquals(AutoStitchStatus.PENDING, scheduler.findConfigById("tenant_abc", futureConfig.id()).orElseThrow().status());
    }
}
