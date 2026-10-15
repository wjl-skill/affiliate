package com.affiliate.platform.service.ai;

import com.affiliate.platform.domain.ai.AiGenerateType;
import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.service.ai.generator.IVideoGenerator;
import com.affiliate.platform.service.ai.generator.VideoGeneratorFactory;
import com.affiliate.platform.service.ai.generator.impl.ArkSeedanceVideoGenerator;
import com.affiliate.platform.service.ai.generator.impl.DashScopeWanVideoGenerator;
import com.affiliate.platform.service.ai.generator.impl.HappyHorseVideoGenerator;
import com.affiliate.platform.service.ai.generator.impl.LocalMockVideoGenerator;
import com.affiliate.platform.service.ai.provider.AiVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import com.affiliate.platform.service.ai.provider.dto.ProviderQueryResult;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitResult;
import com.affiliate.platform.service.ai.provider.impl.ByteDanceArkVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.impl.DashScopeVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.impl.LocalMockVideoProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AI 视频多模型引擎与适配层工厂与生成器测试")
class VideoGeneratorAndProviderTest {

    private VideoProviderFactory providerFactory;
    private VideoGeneratorFactory generatorFactory;

    @BeforeEach
    void setUp() {
        List<AiVideoProviderAdapter> providers = List.of(
                new ByteDanceArkVideoProviderAdapter(),
                new DashScopeVideoProviderAdapter(),
                new LocalMockVideoProviderAdapter()
        );
        providerFactory = new VideoProviderFactory(providers);

        List<IVideoGenerator> generators = List.of(
                new ArkSeedanceVideoGenerator(providerFactory),
                new DashScopeWanVideoGenerator(providerFactory),
                new HappyHorseVideoGenerator(providerFactory),
                new LocalMockVideoGenerator(providerFactory)
        );
        generatorFactory = new VideoGeneratorFactory(generators);
    }

    @Test
    @DisplayName("测试供应商工厂 ProviderFactory 注册与检索")
    void testProviderFactoryLookup() {
        assertTrue(providerFactory.hasProvider("BYTEDANCE_ARK"));
        assertTrue(providerFactory.hasProvider("DASHSCOPE"));
        assertTrue(providerFactory.hasProvider("LOCAL_MOCK"));
        assertFalse(providerFactory.hasProvider("UNKNOWN_PROVIDER"));

        assertNotNull(providerFactory.getProvider("BYTEDANCE_ARK"));
        assertThrows(IllegalArgumentException.class, () -> providerFactory.getProvider("NOT_EXIST"));
    }

    @Test
    @DisplayName("测试生成器工厂 GeneratorFactory 注册与检索")
    void testGeneratorFactoryLookup() {
        assertTrue(generatorFactory.hasGenerator("ark_seedance_2_0"));
        assertTrue(generatorFactory.hasGenerator("dashscope_wan_2_1"));
        assertTrue(generatorFactory.hasGenerator("happyhorse_1_1"));
        assertTrue(generatorFactory.hasGenerator("local_mock_video"));

        IVideoGenerator generator = generatorFactory.getGenerator("ark_seedance_2_0");
        assertEquals("ark_seedance_2_0", generator.supportModelCode());
        assertEquals("BYTEDANCE_ARK", generator.providerCode());
    }

    @Test
    @DisplayName("测试 Seedance 生成器生成任务提交与边界关键帧参数注入")
    void testSeedanceGeneratorSubmit() {
        IVideoGenerator generator = generatorFactory.getGenerator("ark_seedance_2_0");

        VideoGenerateRequest req = new VideoGenerateRequest(
                "ark_seedance_2_0",
                AiGenerateType.IMAGE_TO_VIDEO,
                "Cyberpunk girl running in neon rain",
                "9:16",
                5,
                "https://cdn.example.com/start.png",
                "https://cdn.example.com/end.png", // 具备尾帧，触发 end_frame_control
                List.of(),
                null,
                Map.of(),
                "dept_01",
                "prj_01"
        );

        ProviderSubmitResult result = generator.generate(req);

        assertNotNull(result);
        assertNotNull(result.remoteTaskId());
        assertTrue(result.remoteTaskId().startsWith("ark_task_"));
        assertEquals("PROCESSING", result.status());
    }

    @Test
    @DisplayName("测试 LocalMock 适配器模拟两轮轮询至成功")
    void testLocalMockProviderAdapter() {
        AiVideoProviderAdapter mockProvider = providerFactory.getProvider("LOCAL_MOCK");

        var submitResult = mockProvider.submit(new com.affiliate.platform.service.ai.provider.dto.ProviderSubmitRequest(
                "mock-v1", "Test prompt", "9:16", 5, null, null, List.of(), null, Map.of()
        ));

        assertNotNull(submitResult.remoteTaskId());

        // 第一次轮询：PROCESSING
        ProviderQueryResult poll1 = mockProvider.queryTask(submitResult.remoteTaskId());
        assertEquals(ProviderQueryResult.Status.PROCESSING, poll1.status());

        // 第二次轮询：SUCCEEDED
        ProviderQueryResult poll2 = mockProvider.queryTask(submitResult.remoteTaskId());
        assertEquals(ProviderQueryResult.Status.SUCCEEDED, poll2.status());
        assertNotNull(poll2.videoUrl());
    }
}
