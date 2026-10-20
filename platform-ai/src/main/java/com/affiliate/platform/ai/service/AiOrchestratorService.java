package com.affiliate.platform.ai.service;

import com.affiliate.platform.ai.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 跨模块 AI 能力调度与智能推理中枢编排器 (AI Orchestrator Service)
 * <p>
 * 为 DSP 竞价、素材 Agent 诊断、反作弊风控和报表洞察提供开箱即用、具备自适应容灾降级的统一高阶 API。
 */
@Service
public class AiOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestratorService.class);

    private final AiClient aiClient;
    private final AiModelConfigService configService;
    private final AiPromptTemplateService templateService;
    private final AiUsageMeteringService meteringService;

    public AiOrchestratorService(
            AiClient aiClient,
            AiModelConfigService configService,
            AiPromptTemplateService templateService,
            AiUsageMeteringService meteringService
    ) {
        this.aiClient = aiClient;
        this.configService = configService;
        this.templateService = templateService;
        this.meteringService = meteringService;
    }

    /**
     * 统一模板化 AI 交互入口
     */
    public AiChatResponse chatWithTemplate(
            String tenantId,
            String templateKey,
            Map<String, Object> variables,
            String callerModule
    ) {
        long start = System.currentTimeMillis();
        AiModelConfig config = configService.resolveActiveModel(tenantId);
        var templateOpt = templateService.findTemplate(tenantId, templateKey);

        String systemPrompt = templateOpt.map(AiPromptTemplate::systemPrompt)
                .orElse("You are a professional ad-tech AI assistant.");
        String userPrompt = templateService.renderPrompt(tenantId, templateKey, variables);

        AiChatRequest request = new AiChatRequest(
                tenantId,
                callerModule,
                List.of(
                        AiChatMessage.system(systemPrompt),
                        AiChatMessage.user(userPrompt)
                ),
                config.modelName(),
                config.temperature(),
                config.maxTokens()
        );

        AiChatResponse response;
        String errorMsg = null;
        try {
            response = aiClient.chat(request, config);
        } catch (Exception e) {
            log.warn("[AiOrchestrator] AI execution failed, falling back: {}", e.getMessage());
            errorMsg = e.getMessage();
            response = AiChatResponse.fallback(config.modelName(), "Resilient AI Fallback Executed.", System.currentTimeMillis() - start);
        }

        meteringService.recordUsage(
                tenantId,
                callerModule,
                response.modelName(),
                response.usage(),
                response.latencyMs(),
                response.fallbackUsed(),
                errorMsg
        );

        return response;
    }

    /**
     * 1. 素材 AI 诊断与自省重构
     */
    public String diagnoseCreativeWithAi(
            String tenantId,
            String materialId,
            String diagnosisReason,
            String targetAudience,
            String currentPrompt
    ) {
        Map<String, Object> vars = Map.of(
                "materialId", materialId != null ? materialId : "unknown_material",
                "diagnosisReason", diagnosisReason != null ? diagnosisReason : "Quality drop",
                "targetAudience", targetAudience != null ? targetAudience : "All users",
                "currentPrompt", currentPrompt != null ? currentPrompt : ""
        );
        AiChatResponse resp = chatWithTemplate(tenantId, AiPromptTemplateService.TEMPLATE_CREATIVE_DIAGNOSIS, vars, "CREATIVE");
        return resp.content();
    }

    /**
     * 2. 营销文案衍生裂变生成
     */
    public String generateAdCreativeCopy(
            String tenantId,
            String productName,
            String targetAudience,
            String sellingPoints,
            String callToAction
    ) {
        Map<String, Object> vars = Map.of(
                "productName", productName != null ? productName : "Featured Product",
                "targetAudience", targetAudience != null ? targetAudience : "General Audience",
                "sellingPoints", sellingPoints != null ? sellingPoints : "High Quality & Affordable",
                "callToAction", callToAction != null ? callToAction : "Click Now"
        );
        AiChatResponse resp = chatWithTemplate(tenantId, AiPromptTemplateService.TEMPLATE_AD_COPY_EXPANSION, vars, "CREATIVE");
        return resp.content();
    }

    /**
     * 3. 反作弊欺诈异常 AI 智能诊断
     */
    public String analyzeFraudAnomalyWithAi(
            String tenantId,
            String clickId,
            String fraudReason,
            Map<String, Object> telemetrySummary
    ) {
        Map<String, Object> vars = Map.of(
                "clickId", clickId != null ? clickId : "unknown_click",
                "fraudReason", fraudReason != null ? fraudReason : "Suspicious traffic anomaly",
                "telemetrySummary", telemetrySummary != null ? telemetrySummary.toString() : "N/A"
        );
        AiChatResponse resp = chatWithTemplate(tenantId, AiPromptTemplateService.TEMPLATE_FRAUD_EXPLANATION, vars, "AFFILIATE_FRAUD");
        return resp.content();
    }

    /**
     * 4. DSP 智能出价与竞价博弈咨询
     */
    public String adviseBidOptimizationWithAi(
            String tenantId,
            String campaignId,
            BigDecimal currentBid,
            double eCtr,
            double eCvr,
            BigDecimal floorPrice,
            double winRate
    ) {
        Map<String, Object> vars = Map.of(
                "campaignId", campaignId != null ? campaignId : "unknown_campaign",
                "currentBid", currentBid != null ? currentBid.toString() : "0.00",
                "eCtr", String.format("%.4f", eCtr),
                "eCvr", String.format("%.4f", eCvr),
                "floorPrice", floorPrice != null ? floorPrice.toString() : "0.00",
                "winRate", String.format("%.2f%%", winRate * 100.0)
        );
        AiChatResponse resp = chatWithTemplate(tenantId, AiPromptTemplateService.TEMPLATE_BID_ADVISORY, vars, "DSP_BIDDING");
        return resp.content();
    }

    /**
     * 5. 商业经营与多维报表 AI 洞察总结
     */
    public String synthesizeReportInsightsWithAi(
            String tenantId,
            String dateRange,
            Map<String, Object> metricsSnapshot
    ) {
        Map<String, Object> vars = Map.of(
                "dateRange", dateRange != null ? dateRange : "Recent Period",
                "metricsSnapshot", metricsSnapshot != null ? metricsSnapshot.toString() : "No metrics available"
        );
        AiChatResponse resp = chatWithTemplate(tenantId, AiPromptTemplateService.TEMPLATE_REPORT_SYNTHESIS, vars, "REPORTING");
        return resp.content();
    }
}
