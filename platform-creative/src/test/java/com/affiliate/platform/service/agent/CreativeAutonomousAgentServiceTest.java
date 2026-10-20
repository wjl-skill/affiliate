package com.affiliate.platform.service.agent;

import com.affiliate.platform.domain.agent.CreativeAgentAction;
import com.affiliate.platform.domain.agent.CreativeAgentDiagnosis;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.studio.CapCutDraftExportResult;
import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.service.studio.StudioProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class CreativeAutonomousAgentServiceTest {

    private CreativeAutonomousAgentService agentService;
    private StudioProjectService mockStudioService;

    @BeforeEach
    void setUp() {
        mockStudioService = Mockito.mock(StudioProjectService.class);
        agentService = new CreativeAutonomousAgentService(mockStudioService);
    }

    @Test
    void testHookFatigueDiagnosisAndRefinement() {
        String tenantId = "tenant_ai_01";
        String materialId = "mat_dropoff_high";

        // 前 3 秒跳出率 78%，显著高于 65% 阈值
        CreativeAgentAction action = agentService.triggerAutonomousOptimization(
                tenantId,
                materialId,
                MaterialQualityLevel.RISK,
                0.78,
                0.015,
                0.008,
                BigDecimal.valueOf(0.45),
                "Standard Coffee Mug Promo"
        );

        assertNotNull(action);
        assertTrue(action.revisedPrompt().contains("REFINED_HOOK"));
        assertTrue(action.refinedOpeningHook().contains("Stop scrolling"));

        List<CreativeAgentDiagnosis> history = agentService.getDiagnosisHistory(materialId);
        assertEquals(1, history.size());
        assertEquals(CreativeAgentDiagnosis.DiagnosisType.HOOK_FATIGUE, history.get(0).primaryIssue());
    }

    @Test
    void testCtaIneffectiveDiagnosis() {
        String tenantId = "tenant_ai_01";
        String materialId = "mat_cta_weak";

        // 点击率出众 4.5%，但转化率仅 0.5%
        CreativeAgentAction action = agentService.triggerAutonomousOptimization(
                tenantId,
                materialId,
                MaterialQualityLevel.BLACKHOLE,
                0.40,
                0.045,
                0.005,
                BigDecimal.valueOf(0.30),
                "Smart Fitness Watch Ad"
        );

        assertNotNull(action);
        assertTrue(action.revisedPrompt().contains("HIGH_CONVERTING_CTA"));

        List<CreativeAgentDiagnosis> history = agentService.getDiagnosisHistory(materialId);
        assertEquals(1, history.size());
        assertEquals(CreativeAgentDiagnosis.DiagnosisType.CTA_INEFFECTIVE, history.get(0).primaryIssue());
    }

    @Test
    void testRateLimitingGuardrail() {
        String tenantId = "tenant_ai_01";
        String materialId = "mat_frequent_target";

        // 连续触发 3 次（允许上限）
        for (int i = 0; i < 3; i++) {
            CreativeAgentAction a = agentService.triggerAutonomousOptimization(
                    tenantId, materialId, MaterialQualityLevel.RISK, 0.70, 0.02, 0.01, BigDecimal.valueOf(0.5), "Prompt " + i
            );
            assertNotEquals(CreativeAgentAction.ActionStatus.RATE_LIMITED, a.status());
        }

        // 第 4 次应被防失控熔断拦截
        CreativeAgentAction fourthAction = agentService.triggerAutonomousOptimization(
                tenantId, materialId, MaterialQualityLevel.RISK, 0.70, 0.02, 0.01, BigDecimal.valueOf(0.5), "Prompt 4"
        );
        assertEquals(CreativeAgentAction.ActionStatus.RATE_LIMITED, fourthAction.status());
        assertTrue(fourthAction.executionLog().contains("熔断保护"));
    }

    @Test
    void testFullStudioRebuildAndCapCutExport() {
        String tenantId = "tenant_ai_01";
        String materialId = "mat_full_pipeline";

        StudioProject mockProject = StudioProject.create(
                tenantId,
                "AutoProject",
                "Brief",
                "9:16",
                15,
                "tiktok",
                "ecommerce",
                "live_action",
                "kling_v1",
                "agent"
        );

        Mockito.when(mockStudioService.createProject(eq(tenantId), any()))
                .thenReturn(mockProject);

        CapCutDraftExportResult mockExport = new CapCutDraftExportResult(
                mockProject.id(),
                4,
                15,
                "https://oss.mycdn.com/drafts/" + mockProject.id() + ".zip",
                "https://oss.mycdn.com/packages/" + mockProject.id() + ".zip"
        );
        Mockito.when(mockStudioService.exportCapCutDraft(tenantId, mockProject.id()))
                .thenReturn(mockExport);

        CreativeAgentAction action = agentService.triggerAutonomousOptimization(
                tenantId,
                materialId,
                MaterialQualityLevel.BLACKHOLE,
                0.80,
                0.01,
                0.005,
                BigDecimal.valueOf(0.2),
                "Viral Kitchen Knife"
        );

        assertNotNull(action);
        assertEquals(CreativeAgentAction.ActionStatus.COMPLETED, action.status());
        assertEquals(mockProject.id(), action.newProjectId());
        assertEquals("https://oss.mycdn.com/drafts/" + mockProject.id() + ".zip", action.capCutDraftPath());
    }

    @Test
    void testAiOrchestratorIntegrationAndCopyVariants() {
        com.affiliate.platform.ai.service.AiOrchestratorService mockOrchestrator = Mockito.mock(com.affiliate.platform.ai.service.AiOrchestratorService.class);
        Mockito.when(mockOrchestrator.diagnoseCreativeWithAi(any(), any(), any(), any(), any()))
                .thenReturn("AI Reasoning: Hook lacks visual shock. Introduce dynamic zoom-in.");
        Mockito.when(mockOrchestrator.generateAdCreativeCopy(any(), any(), any(), any(), any()))
                .thenReturn("1. Mind-blowing gadget 2. Flash Sale 3. Buy now");

        CreativeAutonomousAgentService aiAgentService = new CreativeAutonomousAgentService(mockStudioService, mockOrchestrator);

        var refined = aiAgentService.refinePromptAndScript(
                CreativeAgentDiagnosis.DiagnosisType.HOOK_FATIGUE,
                "Wireless Earbuds",
                "tenant_01",
                "mat_ai_99"
        );
        assertNotNull(refined);
        assertTrue(refined.fullRevisedPrompt().contains("AI_REFINED_DIRECTIVE"));
        assertTrue(refined.fullRevisedPrompt().contains("dynamic zoom-in"));

        String copy = aiAgentService.generateCopyVariants("tenant_01", "Earbuds", "Music lovers", "Bass", "Click link");
        assertEquals("1. Mind-blowing gadget 2. Flash Sale 3. Buy now", copy);
    }
}
