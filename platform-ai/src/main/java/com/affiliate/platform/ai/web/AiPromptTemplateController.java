package com.affiliate.platform.ai.web;

import com.affiliate.platform.ai.model.AiPromptTemplate;
import com.affiliate.platform.ai.service.AiPromptTemplateService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 结构化 Prompt 模板管理端点 (AI Prompt Template REST Controller)
 */
@RestController
@RequestMapping("/api/v1/ai/templates")
public class AiPromptTemplateController {

    private final AiPromptTemplateService templateService;

    public AiPromptTemplateController(AiPromptTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<AiPromptTemplate> list() {
        return templateService.listTemplates(TenantContext.get());
    }

    @GetMapping("/{key}")
    public ResponseEntity<AiPromptTemplate> get(@PathVariable String key) {
        return templateService.findTemplate(TenantContext.get(), key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public AiPromptTemplate save(@RequestBody AiPromptTemplate template) {
        return templateService.saveOrUpdate(template);
    }

    @PostMapping("/render")
    public ResponseEntity<Map<String, String>> render(@RequestBody RenderRequest req) {
        String rendered = templateService.renderPrompt(TenantContext.get(), req.templateKey(), req.variables());
        return ResponseEntity.ok(Map.of("templateKey", req.templateKey(), "renderedPrompt", rendered));
    }

    public record RenderRequest(
            String templateKey,
            Map<String, Object> variables
    ) {}
}
