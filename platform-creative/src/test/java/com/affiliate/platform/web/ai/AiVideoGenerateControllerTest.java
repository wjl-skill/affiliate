package com.affiliate.platform.web.ai;

import com.affiliate.platform.domain.ai.*;
import com.affiliate.platform.repository.ai.InMemoryAiGenerateTaskRecordRepository;
import com.affiliate.platform.service.ai.AiVideoGenerationService;
import com.affiliate.platform.service.ai.generator.IVideoGenerator;
import com.affiliate.platform.service.ai.generator.VideoGeneratorFactory;
import com.affiliate.platform.service.ai.generator.impl.LocalMockVideoGenerator;
import com.affiliate.platform.service.ai.meta.AiVideoModelMetaService;
import com.affiliate.platform.service.ai.meta.impl.AiVideoModelMetaServiceImpl;
import com.affiliate.platform.service.ai.provider.AiVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import com.affiliate.platform.service.ai.provider.impl.LocalMockVideoProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AI 视频生成 REST 控制器端点测试")
class AiVideoGenerateControllerTest {

    private AiVideoGenerateController controller;
    private AiVideoGenerationService generationService;
    private AiVideoModelMetaService metaService;

    @BeforeEach
    void setUp() {
        metaService = new AiVideoModelMetaServiceImpl();
        var taskRepo = new InMemoryAiGenerateTaskRecordRepository();

        List<AiVideoProviderAdapter> providers = List.of(new LocalMockVideoProviderAdapter());
        var providerFactory = new VideoProviderFactory(providers);

        List<IVideoGenerator> generators = List.of(new LocalMockVideoGenerator(providerFactory));
        var generatorFactory = new VideoGeneratorFactory(generators);

        generationService = new AiVideoGenerationService(metaService, generatorFactory, providerFactory, taskRepo, event -> {});
        controller = new AiVideoGenerateController(generationService, metaService);
    }

    @Test
    @DisplayName("测试模型元数据查询 GET /api/v1/creative/ai/models")
    void testListAndGetModels() {
        List<AiVideoModelMetaDTO> models = controller.listModels();
        assertFalse(models.isEmpty());

        AiVideoModelMetaDTO model = controller.getModel("local_mock_video");
        assertNotNull(model);
        assertEquals("local_mock_video", model.modelCode());
        assertEquals("LOCAL_MOCK", model.providerCode());
    }

    @Test
    @DisplayName("测试提交生成与单号状态查询 POST /generate, GET /tasks/{taskNo}")
    void testGenerateAndQueryTask() {
        VideoGenerateRequest req = new VideoGenerateRequest(
                "local_mock_video",
                AiGenerateType.TEXT_TO_VIDEO,
                "Controller test prompt",
                "9:16",
                5,
                null,
                null,
                List.of(),
                null,
                Map.of(),
                null,
                null
        );

        AiGenerateTaskRecord created = controller.generate(req);
        assertNotNull(created);
        assertNotNull(created.taskNo());
        assertEquals(AiTaskStatus.PROCESSING, created.status());

        // 查询单号
        AiGenerateTaskRecord queried = controller.getTask(created.taskNo());
        assertEquals(created.id(), queried.id());

        // 测试取消
        AiGenerateTaskRecord cancelled = controller.cancelTask(created.taskNo());
        assertEquals(AiTaskStatus.CANCELLED, cancelled.status());
    }

    @Test
    @DisplayName("测试批量轮询触发端点 POST /tasks/poll")
    void testPollEndpoint() {
        // 先提交一个进行中的任务
        VideoGenerateRequest req = new VideoGenerateRequest(
                "local_mock_video",
                AiGenerateType.TEXT_TO_VIDEO,
                "Prompt to poll",
                "9:16",
                5,
                null,
                null,
                List.of(),
                null,
                Map.of(),
                null,
                null
        );
        controller.generate(req);

        int polledCount = controller.pollTasks();
        assertEquals(1, polledCount);
    }

    @Test
    @DisplayName("测试批量提交生成与批量取消 POST /generate/batch, POST /tasks/batch-cancel")
    void testBatchGenerateAndBatchCancel() {
        VideoGenerateRequest req1 = new VideoGenerateRequest(
                "local_mock_video", AiGenerateType.TEXT_TO_VIDEO, "Batch prompt 1",
                "9:16", 5, null, null, List.of(), null, Map.of(), null, null
        );
        VideoGenerateRequest req2 = new VideoGenerateRequest(
                "local_mock_video", AiGenerateType.TEXT_TO_VIDEO, "Batch prompt 2",
                "9:16", 5, null, null, List.of(), null, Map.of(), null, null
        );

        List<AiGenerateTaskRecord> records = controller.batchGenerate(List.of(req1, req2));
        assertNotNull(records);
        assertEquals(2, records.size());

        List<String> taskNos = records.stream().map(AiGenerateTaskRecord::taskNo).toList();
        List<AiGenerateTaskRecord> cancelledList = controller.batchCancelTasks(taskNos);
        assertNotNull(cancelledList);
        assertEquals(2, cancelledList.size());
        assertTrue(cancelledList.stream().allMatch(t -> t.status() == AiTaskStatus.CANCELLED));
    }
}
