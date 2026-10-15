package com.affiliate.platform.service.studio.pipeline;

import com.affiliate.platform.domain.studio.*;
import com.affiliate.platform.service.studio.pipeline.impl.StoryboardPipelineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StoryboardPipelineService Tests - 6 Pipeline Prompt System")
class StoryboardPipelineServiceTest {

    private StoryboardPipelineServiceImpl pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService = new StoryboardPipelineServiceImpl();
    }

    @Test
    @DisplayName("Should correctly route all 6 industry prompt pipelines")
    void shouldResolvePipelines() {
        assertEquals(AdTypePipeline.ECOMMERCE_SHOWCASE, pipelineService.resolvePipeline("ecommerce"));
        assertEquals(AdTypePipeline.GAMING_INTERACTIVE, pipelineService.resolvePipeline("gaming_app"));
        assertEquals(AdTypePipeline.DRAMA_HOOK_TWIST, pipelineService.resolvePipeline("novel_drama"));
        assertEquals(AdTypePipeline.BRAND_CINEMATIC, pipelineService.resolvePipeline("brand_story"));
        assertEquals(AdTypePipeline.FLASH_SALE_PROMO, pipelineService.resolvePipeline("flash_sale"));
        assertEquals(AdTypePipeline.LOCAL_LIFESTYLE, pipelineService.resolvePipeline("local_life"));
    }

    @Test
    @DisplayName("Should disaggregate Character and Product assets with Flux/SD prompts")
    void shouldAnalyzeAssets() {
        StudioProject project = StudioProject.create(
                "tenant_test",
                "防脱洗发水电商大促",
                "针对职场加班年轻女性，主打28天防脱生发与毛囊激活黑科技",
                "9:16", 30, "tiktok", "ecommerce", "live_action", "ark_seedance_2_0", "creator"
        );

        List<StudioAsset> assets = pipelineService.analyzeAssets(project);
        assertEquals(2, assets.size());

        StudioAsset character = assets.stream().filter(a -> a.assetType() == StudioAssetType.CHARACTER).findFirst().orElseThrow();
        assertEquals("职场知性女性主播", character.name());
        assertTrue(character.promptData().get("prompt").toString().contains("portrait"));

        StudioAsset product = assets.stream().filter(a -> a.assetType() == StudioAssetType.PRODUCT).findFirst().orElseThrow();
        assertEquals("防脱固发精华生发液", product.name());
        assertTrue(product.promptData().get("prompt").toString().contains("Commercial product photography"));
    }

    @Test
    @DisplayName("All 6 pipelines should obey strict scene constraints (3~15s, gold 3s hook, end frame cue, clean voiceover)")
    void shouldObeyStrictSceneConstraints() {
        String[] adTypes = {"ecommerce", "gaming", "drama", "brand", "flash_sale", "local_life"};

        for (String adType : adTypes) {
            StudioProject prj = StudioProject.create(
                    "tenant_test", "测试项目-" + adType, "创意文案简报",
                    "9:16", 30, "tiktok", adType, "live_action", "ark_seedance_2_0", "creator"
            );

            List<StudioScene> scenes = pipelineService.generateStoryboardScenes(prj);
            assertFalse(scenes.isEmpty(), "Pipeline " + adType + " produced empty scenes");

            // 1. 黄金 3 秒 Hook 约束
            StudioScene scene1 = scenes.get(0);
            assertTrue(scene1.durationSeconds() >= 3, "Scene 1 duration must be >= 3s in " + adType);

            for (StudioScene s : scenes) {
                // 2. 单镜时长必须介于 3~15 秒
                assertTrue(s.durationSeconds() >= 3 && s.durationSeconds() <= 15,
                        "Scene duration must be in [3, 15] in " + adType);

                // 3. 收尾画面强制规范
                assertTrue(s.shotPrompt().contains("收尾画面："),
                        "Prompt must contain '收尾画面：' in " + adType);

                // 4. 旁白禁止带有“旁白：”前缀
                assertFalse(s.voiceoverText().startsWith("旁白："),
                        "Voiceover must be clean without prefix in " + adType);

                // 5. 字幕单行字数 <= 15 字
                assertTrue(s.subtitleText().length() <= 20,
                        "Subtitle length exceeded in " + adType);

                // 6. 需带有高亮关键词
                assertNotNull(s.subtitleKeywords());
                assertFalse(s.subtitleKeywords().isEmpty(), "Subtitle keywords missing in " + adType);
            }
        }
    }
}
