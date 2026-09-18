package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.MacroParam;
import com.affiliate.platform.affiliate.domain.PlatformMacroMapping;
import com.affiliate.platform.affiliate.service.AffiliateMacroService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 宏参数与平台映射管理 REST API (Macro Parameter & Platform Mapping Admin Controller)
 * <p>
 * 提供标准宏字典维护、各广告/流量平台宏对照维护，以及追踪链接双向渲染
 * (本平台标准宏 ⇄ 平台原生宏)，用于快速对接不同广告主的宏规范。
 */
@RestController
@RequestMapping("/api/v1/affiliate/macros")
public class AffiliateMacroController {

    private final AffiliateMacroService macroService;

    public AffiliateMacroController(AffiliateMacroService macroService) {
        this.macroService = macroService;
    }

    public record RenderRequest(String platformCode, String url) {}

    // ==========================================
    // 1. 标准宏字典
    // ==========================================
    @GetMapping("/params")
    public List<MacroParam> listParams() {
        return macroService.listParams();
    }

    @PostMapping("/params")
    @ResponseStatus(HttpStatus.CREATED)
    public MacroParam saveParam(@Valid @RequestBody MacroParam param) {
        return macroService.saveParam(param);
    }

    @DeleteMapping("/params/{id}")
    public ResponseEntity<Void> deleteParam(@PathVariable String id) {
        return macroService.deleteParam(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ==========================================
    // 2. 平台宏映射
    // ==========================================
    @GetMapping("/mappings")
    public List<PlatformMacroMapping> listMappings(@RequestParam(required = false) String platformCode) {
        return macroService.listMappings(platformCode);
    }

    @GetMapping("/platforms")
    public List<Map<String, Object>> listPlatforms() {
        return macroService.listPlatforms();
    }

    @PostMapping("/mappings")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformMacroMapping saveMapping(@Valid @RequestBody PlatformMacroMapping mapping) {
        return macroService.saveMapping(mapping);
    }

    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable String id) {
        return macroService.deleteMapping(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ==========================================
    // 3. 链接双向渲染
    // ==========================================

    /** 标准宏 → 平台宏 (生成交付给广告主的平台定制追踪链接) */
    @PostMapping("/render")
    public AffiliateMacroService.RenderResult renderToPlatform(@RequestBody RenderRequest req) {
        return macroService.renderToPlatform(req.platformCode(), req.url());
    }

    /** 平台宏 → 标准宏 (把广告主提供的平台原生链接反解为标准模板) */
    @PostMapping("/standardize")
    public AffiliateMacroService.RenderResult renderToStandard(@RequestBody RenderRequest req) {
        return macroService.renderToStandard(req.platformCode(), req.url());
    }
}
