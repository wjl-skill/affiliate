package com.affiliate.platform.ai;

import com.affiliate.platform.ai.model.AiPromptTemplate;
import com.affiliate.platform.ai.service.AiPromptTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AiPromptTemplateServiceTest {

    private AiPromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new AiPromptTemplateService();
    }

    @Test
    void testPreSeededTemplatesExist() {
        List<AiPromptTemplate> templates = service.listTemplates("default");
        assertTrue(templates.size() >= 5);

        Optional<AiPromptTemplate> creativeTpl = service.findTemplate("default", AiPromptTemplateService.TEMPLATE_CREATIVE_DIAGNOSIS);
        assertTrue(creativeTpl.isPresent());
        assertTrue(creativeTpl.get().userPromptTemplate().contains("{{materialId}}"));

        Optional<AiPromptTemplate> bidTpl = service.findTemplate("default", AiPromptTemplateService.TEMPLATE_BID_ADVISORY);
        assertTrue(bidTpl.isPresent());
        assertTrue(bidTpl.get().userPromptTemplate().contains("{{currentBid}}"));
    }

    @Test
    void testRenderVariables() {
        Map<String, Object> vars = Map.of(
                "materialId", "mat_hero_999",
                "diagnosisReason", "Hook 3s Drop > 80%",
                "targetAudience", "US Female 18-24",
                "currentPrompt", "Check out this app now!"
        );

        String rendered = service.renderPrompt("default", AiPromptTemplateService.TEMPLATE_CREATIVE_DIAGNOSIS, vars);
        assertNotNull(rendered);
        assertTrue(rendered.contains("mat_hero_999"));
        assertTrue(rendered.contains("Hook 3s Drop > 80%"));
        assertTrue(rendered.contains("US Female 18-24"));
        assertFalse(rendered.contains("{{materialId}}"));
    }

    @Test
    void testCustomTenantOverride() {
        AiPromptTemplate custom = AiPromptTemplate.create(
                "tenant_custom",
                AiPromptTemplateService.TEMPLATE_AD_COPY_EXPANSION,
                "Custom Ad Copy",
                "Custom System Prompt",
                "Custom User {{productName}} with CTA {{callToAction}}"
        );
        service.saveOrUpdate(custom);

        Optional<AiPromptTemplate> retrieved = service.findTemplate("tenant_custom", AiPromptTemplateService.TEMPLATE_AD_COPY_EXPANSION);
        assertTrue(retrieved.isPresent());
        assertEquals("Custom System Prompt", retrieved.get().systemPrompt());

        String rendered = service.renderPrompt("tenant_custom", AiPromptTemplateService.TEMPLATE_AD_COPY_EXPANSION, Map.of(
                "productName", "AI Fitness Tracker",
                "callToAction", "Get 50% Off"
        ));
        assertEquals("Custom User AI Fitness Tracker with CTA Get 50% Off", rendered);
    }
}
