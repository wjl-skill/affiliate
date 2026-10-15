package com.affiliate.platform.service.video;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.DiskHealthStatus;
import com.affiliate.platform.domain.video.VideoProcessStatus;
import com.affiliate.platform.domain.video.VideoProcessTaskRecord;
import com.affiliate.platform.domain.video.VideoProcessType;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.video.InMemoryVideoProcessTaskRepository;
import com.affiliate.platform.service.video.impl.VideoProcessServiceImpl;
import com.affiliate.platform.service.video.operator.*;
import com.affiliate.platform.service.video.probe.impl.MediaProbeServiceImpl;
import com.affiliate.platform.service.video.storage.DiskHighWatermarkException;
import com.affiliate.platform.service.video.storage.WorkDirManager;
import com.affiliate.platform.web.video.dto.VideoProcessRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VideoProcessService & WorkDirManager Tests")
class VideoProcessServiceAndDiskManagerTest {

    private InMemoryVideoProcessTaskRepository taskRepo;
    private WorkDirManager workDirManager;
    private VideoOperatorFactory operatorFactory;
    private MediaProbeServiceImpl probeService;
    private VideoProcessServiceImpl processService;
    private List<DomainEvent<?>> publishedEvents;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        taskRepo = new InMemoryVideoProcessTaskRepository();
        workDirManager = new WorkDirManager();
        ReflectionTestUtils.setField(workDirManager, "baseWorkspaceDir", tempDir.toAbsolutePath().toString());
        workDirManager.setHighWatermarkThreshold(80.0);
        workDirManager.setSimulatedSpace(10_000_000_000L, 7_000_000_000L); // 30% used, safely under 80%

        List<IVideoOperator> operators = List.of(
                new WatermarkRemovalOperator(),
                new SubtitleProcessorOperator(),
                new IntroSplicingOperator(),
                new CtaOverlayOperator(),
                new TrimOperator()
        );
        operatorFactory = new VideoOperatorFactory(operators);
        probeService = new MediaProbeServiceImpl();

        publishedEvents = new ArrayList<>();
        EventPublisher mockPublisher = publishedEvents::add;

        processService = new VideoProcessServiceImpl(
                taskRepo,
                operatorFactory,
                probeService,
                workDirManager,
                mockPublisher,
                Runnable::run
        );
    }

    @Test
    @DisplayName("WorkDirManager should create and clean sandbox workspace directory safely")
    void testWorkDirLifecycle() throws Exception {
        Path workDir = workDirManager.createSandboxWorkDir("task_test_123");
        assertNotNull(workDir);
        assertTrue(Files.exists(workDir));

        // Create temporary slice files
        Path slice1 = workDir.resolve("slice1.mp4");
        Files.writeString(slice1, "slice_content");
        assertTrue(Files.exists(slice1));

        // Clean directory
        workDirManager.cleanWorkDir(workDir);
        assertFalse(Files.exists(workDir));
        assertFalse(Files.exists(slice1));
    }

    @Test
    @DisplayName("DiskHealthStatus should correctly evaluate usage and trigger alert on high watermark")
    void testDiskHealthEvaluation() {
        // Safe condition (30% used)
        DiskHealthStatus safe = DiskHealthStatus.evaluate(1000, 700, 80.0);
        assertTrue(safe.isSafe());
        assertEquals(30.0, safe.usedPercent());

        // Unsafe condition (85% used)
        DiskHealthStatus unsafe = DiskHealthStatus.evaluate(1000, 150, 80.0);
        assertFalse(unsafe.isSafe());
        assertEquals(85.0, unsafe.usedPercent());
        assertTrue(unsafe.statusDescription().contains("高水位熔断保护"));
    }

    @Test
    @DisplayName("High watermark should throw DiskHighWatermarkException when usage exceeds threshold")
    void testHighWatermarkDefenseTrigger() {
        // 模拟磁盘已用 90%，超出 80% 阈值
        workDirManager.setSimulatedSpace(10_000_000_000L, 1_000_000_000L);

        assertThrows(DiskHighWatermarkException.class, () -> {
            workDirManager.checkDiskHighWatermarkDefense();
        });

        // Submitting task should also fail with high watermark defense
        VideoProcessRequest request = new VideoProcessRequest(
                VideoProcessType.TRIM,
                "https://oss.affiliate.com/sample.mp4",
                null, 1080, 1920, AlignMode.LETTERBOX,
                Map.of("startSeconds", 0.0, "endSeconds", 5.0)
        );

        assertThrows(DiskHighWatermarkException.class, () -> {
            processService.submitProcessTask("t_demo", request);
        });
    }

    @Test
    @DisplayName("Should successfully submit and execute video processing pipeline")
    void testSubmitAndExecutePipeline() {
        VideoProcessRequest request = new VideoProcessRequest(
                VideoProcessType.INTRO_STITCH,
                "https://oss.affiliate.com/body.mp4",
                "https://oss.affiliate.com/hook.mp4",
                1080, 1920, AlignMode.LETTERBOX,
                Map.of()
        );

        VideoProcessTaskRecord submitted = processService.submitProcessTask("t_demo", request);
        assertNotNull(submitted);
        assertEquals(VideoProcessStatus.PENDING, submitted.status());
        assertNotNull(submitted.taskNo());

        // Synchronously execute the pipeline
        processService.executeTaskPipeline(submitted.id());

        Optional<VideoProcessTaskRecord> finishedOpt = processService.getTaskByTaskNo("t_demo", submitted.taskNo());
        assertTrue(finishedOpt.isPresent());
        VideoProcessTaskRecord finished = finishedOpt.get();

        assertEquals(VideoProcessStatus.SUCCESS, finished.status());
        assertNotNull(finished.outputVideoUrl());
        assertTrue(finished.outputVideoUrl().contains(submitted.taskNo()));
        assertNotNull(finished.outputCoverUrl());
        assertNotNull(finished.finishedAt());

        // Verify domain event publication
        assertFalse(publishedEvents.isEmpty());
        DomainEvent<?> event = publishedEvents.get(0);
        assertEquals("creative.video.processed.v1", event.eventType());
        assertEquals("t_demo", event.tenantId());
        assertEquals(submitted.taskNo(), event.aggregateId());
    }

    @Test
    @DisplayName("Should list recent tasks correctly")
    void testListRecentTasks() {
        for (int i = 0; i < 3; i++) {
            VideoProcessRequest req = new VideoProcessRequest(
                    VideoProcessType.TRIM,
                    "video_" + i + ".mp4",
                    null, 1080, 1920, AlignMode.LETTERBOX,
                    Map.of("startSeconds", 0.0, "endSeconds", 1.0)
            );
            processService.submitProcessTask("t_demo", req);
        }

        List<VideoProcessTaskRecord> recent = processService.listRecentTasks("t_demo", 10);
        assertEquals(3, recent.size());
    }
}
