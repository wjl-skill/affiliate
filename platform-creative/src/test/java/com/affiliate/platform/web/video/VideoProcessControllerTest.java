package com.affiliate.platform.web.video;

import com.affiliate.platform.domain.video.*;
import com.affiliate.platform.repository.video.InMemoryAutoStitchConfigRepository;
import com.affiliate.platform.repository.video.InMemoryVideoProcessTaskRepository;
import com.affiliate.platform.service.video.AutoStitchService;
import com.affiliate.platform.service.video.impl.AutoStitchServiceImpl;
import com.affiliate.platform.service.video.impl.VideoProcessServiceImpl;
import com.affiliate.platform.service.video.operator.*;
import com.affiliate.platform.service.video.probe.impl.MediaProbeServiceImpl;
import com.affiliate.platform.service.video.scheduler.AutoStitchScheduler;
import com.affiliate.platform.service.video.storage.WorkDirManager;
import com.affiliate.platform.web.video.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VideoProcessController REST Endpoints Tests")
class VideoProcessControllerTest {

    private VideoProcessController controller;
    private VideoProcessServiceImpl processService;
    private MediaProbeServiceImpl probeService;
    private AutoStitchScheduler autoStitchScheduler;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var taskRepo = new InMemoryVideoProcessTaskRepository();
        var configRepo = new InMemoryAutoStitchConfigRepository();
        AutoStitchService autoStitchService = new AutoStitchServiceImpl();

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
        probeService = new MediaProbeServiceImpl();

        processService = new VideoProcessServiceImpl(taskRepo, factory, probeService, workDirManager, e -> {}, Runnable::run);
        autoStitchScheduler = new AutoStitchScheduler(configRepo, autoStitchService, processService);

        controller = new VideoProcessController(processService, probeService, autoStitchScheduler);
    }

    @Test
    @DisplayName("POST /probe should extract video technical metadata")
    void testProbeEndpoint() {
        VideoProbeRequest req = new VideoProbeRequest("https://oss.affiliate.com/sample_1080x1920.mp4");
        VideoProbeResult result = controller.probe(req);

        assertNotNull(result);
        assertEquals(1080, result.width());
        assertEquals(1920, result.height());
        assertEquals("9:16", result.aspectRatio());
        assertEquals(30.0, result.fps());
    }

    @Test
    @DisplayName("POST /process and GET /tasks/{taskNo} should submit and retrieve task")
    void testProcessAndGetTask() {
        VideoProcessRequest req = new VideoProcessRequest(
                VideoProcessType.WATERMARK_REMOVE,
                "https://oss.affiliate.com/raw_video.mp4",
                null,
                1080, 1920,
                AlignMode.LETTERBOX,
                Map.of("x", 100, "y", 50, "w", 200, "h", 80)
        );

        VideoProcessTaskRecord task = controller.submitProcessTask(req);
        assertNotNull(task);
        assertNotNull(task.taskNo());
        assertEquals(VideoProcessType.WATERMARK_REMOVE, task.processType());

        VideoProcessTaskRecord retrieved = controller.getTask(task.taskNo());
        assertNotNull(retrieved);
        assertEquals(task.taskNo(), retrieved.taskNo());

        List<VideoProcessTaskRecord> recent = controller.listRecentTasks(10);
        assertFalse(recent.isEmpty());
    }

    @Test
    @DisplayName("POST /auto-stitch/configs, GET /configs, and POST /trigger endpoints")
    void testAutoStitchConfigEndpoints() {
        AutoStitchCreateRequest req = new AutoStitchCreateRequest(
                "Auto_Test_Job",
                List.of("hook_a.mp4", "hook_b.mp4"),
                List.of("body_1.mp4", "body_2.mp4"),
                "9:16",
                AlignMode.LETTERBOX,
                Map.of("ctaText", "立即加入"),
                null
        );

        AutoStitchConfig config = controller.createAutoStitchConfig(req);
        assertNotNull(config);
        assertNotNull(config.id());
        assertEquals(AutoStitchStatus.PENDING, config.status());

        AutoStitchConfig queried = controller.getAutoStitchConfig(config.id());
        assertEquals("Auto_Test_Job", queried.configName());

        List<AutoStitchConfig> list = controller.listAutoStitchConfigs();
        assertEquals(1, list.size());

        // Trigger batch execution: 2 x 2 = 4 tasks
        AutoStitchTriggerResult triggerRes = controller.triggerAutoStitchConfig(config.id());
        assertNotNull(triggerRes);
        assertEquals(4, triggerRes.generatedTaskCount());
        assertEquals(4, triggerRes.taskNos().size());

        // Check updated status
        AutoStitchConfig afterTrigger = controller.getAutoStitchConfig(config.id());
        assertEquals(AutoStitchStatus.EXECUTED, afterTrigger.status());
    }

    @Test
    @DisplayName("GET /disk-status should report host storage health")
    void testDiskStatusEndpoint() {
        DiskHealthStatus status = controller.getDiskStatus();
        assertNotNull(status);
        assertTrue(status.totalSpaceBytes() > 0);
        assertTrue(status.isSafe());
        assertNotNull(status.statusDescription());
    }

    @Test
    @DisplayName("POST /process/batch should submit multiple video processing tasks concurrently")
    void testBatchProcessTasks() {
        VideoProcessRequest req1 = new VideoProcessRequest(
                VideoProcessType.WATERMARK_REMOVE,
                "https://oss.affiliate.com/raw_1.mp4",
                null, 1080, 1920, AlignMode.LETTERBOX, Map.of()
        );
        VideoProcessRequest req2 = new VideoProcessRequest(
                VideoProcessType.SUBTITLE,
                "https://oss.affiliate.com/raw_2.mp4",
                null, 1080, 1920, AlignMode.LETTERBOX, Map.of("subtitleText", "Hello")
        );

        List<VideoProcessTaskRecord> records = controller.submitBatchProcessTasks(List.of(req1, req2));
        assertNotNull(records);
        assertEquals(2, records.size());
        assertEquals(VideoProcessType.WATERMARK_REMOVE, records.get(0).processType());
        assertEquals(VideoProcessType.SUBTITLE, records.get(1).processType());
    }

    @Test
    @DisplayName("DELETE /auto-stitch/configs/{id} and PUT /auto-stitch/configs/{id}/status should manage config lifecycle")
    void testAutoStitchConfigLifecycleEndpoints() {
        AutoStitchCreateRequest req = new AutoStitchCreateRequest(
                "Lifecycle_Config",
                List.of("hook1.mp4"),
                List.of("body1.mp4"),
                "9:16",
                AlignMode.LETTERBOX,
                Map.of(),
                null
        );

        AutoStitchConfig config = controller.createAutoStitchConfig(req);
        assertNotNull(config);

        // Update status to CANCELLED
        AutoStitchConfig updated = controller.updateAutoStitchConfigStatus(config.id(), AutoStitchStatus.CANCELLED);
        assertEquals(AutoStitchStatus.CANCELLED, updated.status());

        // Delete config
        controller.deleteAutoStitchConfig(config.id());
        assertThrows(IllegalArgumentException.class, () -> controller.getAutoStitchConfig(config.id()));
    }
}
