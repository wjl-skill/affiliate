package com.affiliate.platform.service.ai;

import com.affiliate.platform.domain.ai.*;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.ai.InMemoryAiGenerateTaskRecordRepository;
import com.affiliate.platform.service.ai.generator.IVideoGenerator;
import com.affiliate.platform.service.ai.generator.VideoGeneratorFactory;
import com.affiliate.platform.service.ai.generator.impl.ArkSeedanceVideoGenerator;
import com.affiliate.platform.service.ai.generator.impl.DashScopeWanVideoGenerator;
import com.affiliate.platform.service.ai.generator.impl.LocalMockVideoGenerator;
import com.affiliate.platform.service.ai.meta.AiVideoModelMetaService;
import com.affiliate.platform.service.ai.meta.impl.AiVideoModelMetaServiceImpl;
import com.affiliate.platform.service.ai.provider.AiVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import com.affiliate.platform.service.ai.provider.impl.ByteDanceArkVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.impl.DashScopeVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.impl.LocalMockVideoProviderAdapter;
import com.affiliate.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AI 视频生成应用服务完整生命周期与轮询转存测试")
class AiVideoGenerationServiceTest {

    private AiVideoGenerationService service;
    private InMemoryAiGenerateTaskRecordRepository taskRepo;
    private AiVideoModelMetaService metaService;
    private List<DomainEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant_ai_test");
        taskRepo = new InMemoryAiGenerateTaskRecordRepository();
        metaService = new AiVideoModelMetaServiceImpl();
        publishedEvents = new ArrayList<>();

        List<AiVideoProviderAdapter> providers = List.of(
                new ByteDanceArkVideoProviderAdapter(),
                new DashScopeVideoProviderAdapter(),
                new LocalMockVideoProviderAdapter()
        );
        VideoProviderFactory providerFactory = new VideoProviderFactory(providers);

        List<IVideoGenerator> generators = List.of(
                new ArkSeedanceVideoGenerator(providerFactory),
                new DashScopeWanVideoGenerator(providerFactory),
                new LocalMockVideoGenerator(providerFactory)
        );
        VideoGeneratorFactory generatorFactory = new VideoGeneratorFactory(generators);

        EventPublisher testPublisher = publishedEvents::add;
        service = new AiVideoGenerationService(metaService, generatorFactory, providerFactory, taskRepo, testPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.set(null);
    }

    @Test
    @DisplayName("测试任务提交流程：入参校验通过，初始化并流转至 PROCESSING")
    void testSubmitTaskSuccess() {
        VideoGenerateRequest req = new VideoGenerateRequest(
                "ark_seedance_2_0",
                AiGenerateType.TEXT_TO_VIDEO,
                "A futuristic flying car over cyberpunk city",
                "9:16",
                5,
                null,
                null,
                List.of(),
                null,
                Map.of(),
                "dept_marketing",
                "prj_cyberpunk"
        );

        AiGenerateTaskRecord record = service.submitTask(req, "test_user");

        assertNotNull(record);
        assertNotNull(record.taskNo());
        assertEquals(AiTaskStatus.PROCESSING, record.status());
        assertNotNull(record.remoteTaskId());
        assertEquals("ark_seedance_2_0", record.modelCode());
        assertEquals("BYTEDANCE_ARK", record.providerCode());

        // 验证领域事件
        assertEquals(1, publishedEvents.size());
        assertEquals("creative.ai.task.submitted.v1", publishedEvents.getFirst().eventType());
    }

    @Test
    @DisplayName("测试图生视频参数缺失校验：缺少 startImageUrl 抛出 IllegalArgumentException")
    void testImageToVideoValidationFailure() {
        VideoGenerateRequest req = new VideoGenerateRequest(
                "ark_seedance_2_0",
                AiGenerateType.IMAGE_TO_VIDEO, // 图生视频
                "Make this picture dynamic",
                "9:16",
                5,
                null, // 缺失起始图片！
                null,
                List.of(),
                null,
                Map.of(),
                "dept_01",
                "prj_01"
        );

        assertThrows(IllegalArgumentException.class, () -> service.submitTask(req, "user_01"));
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    @DisplayName("测试任务取消流程：非终态任务成功取消为 CANCELLED")
    void testCancelTask() {
        VideoGenerateRequest req = new VideoGenerateRequest(
                "local_mock_video",
                AiGenerateType.TEXT_TO_VIDEO,
                "Test cancel prompt",
                "16:9",
                5,
                null,
                null,
                List.of(),
                null,
                Map.of(),
                null,
                null
        );

        AiGenerateTaskRecord record = service.submitTask(req, "user_cancel");
        assertEquals(AiTaskStatus.PROCESSING, record.status());

        AiGenerateTaskRecord cancelled = service.cancelTask(record.taskNo());
        assertEquals(AiTaskStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.status().isTerminal());

        // 验证发布了取消事件
        assertTrue(publishedEvents.stream().anyMatch(e -> "creative.ai.task.cancelled.v1".equals(e.eventType())));
    }

    @Test
    @DisplayName("测试轮询完成生命周期：拉取到上游成功结果并自动转存 OSS 与算力核算")
    void testPollingTaskSucceeded() {
        // 使用 LocalMockVideo 提交任务
        VideoGenerateRequest req = new VideoGenerateRequest(
                "local_mock_video",
                AiGenerateType.TEXT_TO_VIDEO,
                "Test mock prompt",
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

        AiGenerateTaskRecord record = service.submitTask(req, "mock_user");
        assertEquals(AiTaskStatus.PROCESSING, record.status());

        // 第一次轮询：Mock 处于 PROCESSING
        service.pollSingleTask(record);
        AiGenerateTaskRecord afterPoll1 = service.getTask(record.taskNo()).orElseThrow();
        assertEquals(AiTaskStatus.PROCESSING, afterPoll1.status());

        // 第二次轮询：Mock 触发 SUCCEEDED -> 流转至 SUCCESS 并生成 OSS 地址
        service.pollSingleTask(afterPoll1);
        AiGenerateTaskRecord afterPoll2 = service.getTask(record.taskNo()).orElseThrow();
        assertEquals(AiTaskStatus.SUCCESS, afterPoll2.status());
        assertNotNull(afterPoll2.ossUrl());
        assertTrue(afterPoll2.ossUrl().startsWith("https://oss.affiliate.com/creatives/"));
        assertEquals("CALCULATED", afterPoll2.costStatus());

        // 验证完成事件
        assertTrue(publishedEvents.stream().anyMatch(e -> "creative.ai.task.succeeded.v1".equals(e.eventType())));
    }
}
