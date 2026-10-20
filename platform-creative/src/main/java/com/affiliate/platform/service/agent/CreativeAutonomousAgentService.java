package com.affiliate.platform.service.agent;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import com.affiliate.platform.domain.agent.CreativeAgentAction;
import com.affiliate.platform.domain.agent.CreativeAgentDiagnosis;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.studio.CapCutDraftExportResult;
import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.service.studio.StudioProjectService;
import com.affiliate.platform.web.studio.dto.StudioProjectCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 生产级营销创意自治闭环 Agent 核心服务 (Creative Autonomous Agent Service)
 * <p>
 * 解决投放素材与制作脱节的断层痛点：
 * 1. 自动感知素材质量大盘状态（针对 BLACKHOLE、RISK 等劣质/衰退素材）；
 * 2. 智能深度归因流失漏斗：前 3 秒流失 (HOOK_FATIGUE)、转化无力 (CTA_INEFFECTIVE) 或审美疲劳 (AUDIENCE_FATIGUE)；
 * 3. 驱动 Prompt Self-Refine：针对性重写黄金 3 秒痛点分镜与结尾紧迫感 CTA；
 * 4. 自动化调用 StudioProjectService 重构分镜工程并导出 CapCut 标准 4 轨道草稿包；
 * 5. 防失控熔断机制 (Runaway Guardrail)：单素材 24 小时内最多允许 3 次自治迭代。
 */
@Service
public class CreativeAutonomousAgentService {

    private static final Logger log = LoggerFactory.getLogger(CreativeAutonomousAgentService.class);

    // 24 小时内最大自治迭代次数
    public static final int MAX_DAILY_OPTIMIZATIONS = 3;

    private final StudioProjectService studioProjectService;
    private final AiOrchestratorService aiOrchestratorService;

    // 历史诊断记录：materialId -> 诊断历史
    private final ConcurrentMap<String, List<CreativeAgentDiagnosis>> diagnosisHistory = new ConcurrentHashMap<>();
    // 历史调优执行流水：actionId -> Action
    private final ConcurrentMap<String, CreativeAgentAction> actionStore = new ConcurrentHashMap<>();
    // 素材维度的优化时间戳记录 (用于频控)：materialId -> [Timestamps]
    private final ConcurrentMap<String, List<Instant>> materialOptimizationTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public CreativeAutonomousAgentService(
            @Autowired(required = false) StudioProjectService studioProjectService,
            @Autowired(required = false) AiOrchestratorService aiOrchestratorService
    ) {
        this.studioProjectService = studioProjectService;
        this.aiOrchestratorService = aiOrchestratorService;
    }

    public CreativeAutonomousAgentService(StudioProjectService studioProjectService) {
        this(studioProjectService, null);
    }

    public CreativeAutonomousAgentService() {
        this(null, null);
    }

    /**
     * 对低质或衰退素材执行端到端自治诊断与闭环重构
     *
     * @param tenantId      租户 ID
     * @param materialId    素材 ID
     * @param currentLevel  当前质量等级
     * @param dropOff3s     前 3 秒跳出流失率 (0.0 ~ 1.0)
     * @param ctr           点击率 (0.0 ~ 1.0)
     * @param cvr           转化率 (0.0 ~ 1.0)
     * @param roi           当前投产比
     * @param originalPrompt 原始制作提示词/剧本
     * @return 自治调优执行快照
     */
    public CreativeAgentAction triggerAutonomousOptimization(
            String tenantId,
            String materialId,
            MaterialQualityLevel currentLevel,
            double dropOff3s,
            double ctr,
            double cvr,
            BigDecimal roi,
            String originalPrompt
    ) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";

        // 1. 执行防失控频控检查 (Rate Limiting Check)
        if (isRateLimited(materialId)) {
            String actionId = UUID.randomUUID().toString();
            CreativeAgentAction limitedAction = new CreativeAgentAction(
                    actionId,
                    effectiveTenant,
                    materialId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    CreativeAgentAction.ActionStatus.RATE_LIMITED,
                    "24h 内自治优化次数已达上限 (" + MAX_DAILY_OPTIMIZATIONS + " 次)，触发防失控熔断保护",
                    Instant.now()
            );
            actionStore.put(actionId, limitedAction);
            log.warn("Creative agent optimization rate-limited for material: {}", materialId);
            return limitedAction;
        }

        // 2. 深度根因诊断 (Root Cause Diagnosis)
        CreativeAgentDiagnosis diagnosis = diagnosePerformance(
                effectiveTenant, materialId, currentLevel, dropOff3s, ctr, cvr, roi
        );
        diagnosisHistory.computeIfAbsent(materialId, k -> Collections.synchronizedList(new ArrayList<>())).add(diagnosis);

        // 3. 执行 Prompt Self-Refine 生成改版策略（优先大模型自主推理，具备确定性领域规则兜底）
        RefinedScript refinedScript = refinePromptAndScript(diagnosis.primaryIssue(), originalPrompt, effectiveTenant, materialId);

        String actionId = UUID.randomUUID().toString();
        recordOptimizationTimestamp(materialId);

        String newProjectId = null;
        String capcutPath = null;
        CreativeAgentAction.ActionStatus finalStatus = CreativeAgentAction.ActionStatus.SCRIPT_REFINED;
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("诊断完成: ").append(diagnosis.primaryIssue()).append("; 剧本完成 Self-Refine; ");

        // 4. 若长视频工作台已装配，联动创建新长视频工程并导出剪映工程
        if (studioProjectService != null) {
            try {
                StudioProjectCreateRequest createReq = new StudioProjectCreateRequest(
                        "AutoRefined_" + materialId + "_" + System.currentTimeMillis(),
                        refinedScript.fullRevisedPrompt(),
                        "9:16",
                        15,
                        "TIKTOK",
                        "FEED",
                        "DYNAMIC",
                        "kling_v1"
                );

                StudioProject createdProject = studioProjectService.createProject(effectiveTenant, createReq);
                if (createdProject != null) {
                    newProjectId = createdProject.id();
                    studioProjectService.analyzeAndSaveAssets(effectiveTenant, newProjectId);
                    studioProjectService.generateStoryboards(effectiveTenant, newProjectId);
                    studioProjectService.prepareBoundaryKeyframes(effectiveTenant, newProjectId);

                    CapCutDraftExportResult exportResult = studioProjectService.exportCapCutDraft(effectiveTenant, newProjectId);
                    if (exportResult != null && exportResult.draftUrl() != null && !exportResult.draftUrl().isBlank()) {
                        capcutPath = exportResult.draftUrl();
                        finalStatus = CreativeAgentAction.ActionStatus.COMPLETED;
                        logBuilder.append("已自动生成新工程 ").append(newProjectId).append(" 并成功导出剪映 4 轨道草稿包");
                    } else {
                        finalStatus = CreativeAgentAction.ActionStatus.PROJECT_REGENERATED;
                        logBuilder.append("新工程已生成，草稿导出降级");
                    }
                }
            } catch (Exception e) {
                log.error("Failed to automatically rebuild studio project for material: {}", materialId, e);
                logBuilder.append("工程生成异常: ").append(e.getMessage());
            }
        } else {
            logBuilder.append("长视频服务未挂载，已保留优化提示词与分镜策略");
        }

        CreativeAgentAction action = new CreativeAgentAction(
                actionId,
                effectiveTenant,
                materialId,
                null,
                newProjectId,
                refinedScript.fullRevisedPrompt(),
                refinedScript.openingHook(),
                refinedScript.closingCta(),
                capcutPath,
                finalStatus,
                logBuilder.toString(),
                Instant.now()
        );

        actionStore.put(actionId, action);
        log.info("Creative agent successfully executed autonomous action: id={}, materialId={}, status={}",
                actionId, materialId, finalStatus);
        return action;
    }

    /**
     * 智能漏斗流失诊断
     */
    public CreativeAgentDiagnosis diagnosePerformance(
            String tenantId,
            String materialId,
            MaterialQualityLevel level,
            double dropOff3s,
            double ctr,
            double cvr,
            BigDecimal roi
    ) {
        CreativeAgentDiagnosis.DiagnosisType type;
        String analysis;

        if (dropOff3s > 0.65) {
            type = CreativeAgentDiagnosis.DiagnosisType.HOOK_FATIGUE;
            analysis = String.format(Locale.ROOT, "前 3 秒跳出率高达 %.1f%%，严重缺乏吸睛冲突悬念，导致用户前序流失", dropOff3s * 100);
        } else if (cvr < 0.015 && ctr >= 0.02) {
            type = CreativeAgentDiagnosis.DiagnosisType.CTA_INEFFECTIVE;
            analysis = String.format(Locale.ROOT, "中段点击率达 %.2f%%，但后端转化仅 %.2f%%，结尾行动号召（CTA）缺乏紧迫利益点", ctr * 100, cvr * 100);
        } else if (roi.compareTo(BigDecimal.valueOf(0.60)) < 0 && dropOff3s < 0.50) {
            type = CreativeAgentDiagnosis.DiagnosisType.COST_OVERRUN;
            analysis = String.format(Locale.ROOT, "播放完成度尚可但投产比仅 %s，单次转化成本过高，需重塑核心卖点说服力", roi);
        } else if (level == MaterialQualityLevel.BLACKHOLE) {
            type = CreativeAgentDiagnosis.DiagnosisType.AUDIENCE_FATIGUE;
            analysis = "已被大盘识别为黑洞素材，可能已进入受众疲劳衰退期，建议更换首镜头主体与配乐情绪";
        } else {
            type = CreativeAgentDiagnosis.DiagnosisType.UNKNOWN_DECLINE;
            analysis = "综合多维效果波动，执行全面开场与号召双向优化迭代";
        }

        return new CreativeAgentDiagnosis(
                materialId,
                tenantId,
                level != null ? level : MaterialQualityLevel.RISK,
                type,
                analysis,
                dropOff3s,
                ctr,
                cvr,
                roi,
                Instant.now()
        );
    }

    /**
     * 基于流失归因诊断，执行 Prompt Self-Refine
     */
    public RefinedScript refinePromptAndScript(CreativeAgentDiagnosis.DiagnosisType issue, String basePrompt) {
        return refinePromptAndScript(issue, basePrompt, "default", "unknown_material");
    }

    public RefinedScript refinePromptAndScript(
            CreativeAgentDiagnosis.DiagnosisType issue,
            String basePrompt,
            String tenantId,
            String materialId
    ) {
        String cleanBase = (basePrompt != null && !basePrompt.isBlank()) ? basePrompt.trim() : "Premium Modern Lifestyle Product";

        // 1. 优先尝试大模型自主推理自省重构
        if (aiOrchestratorService != null) {
            try {
                String aiDiagnosis = aiOrchestratorService.diagnoseCreativeWithAi(
                        tenantId,
                        materialId,
                        issue != null ? issue.name() : "PERFORMANCE_DROP",
                        "Target Audience 18-35",
                        cleanBase
                );
                if (aiDiagnosis != null && !aiDiagnosis.isBlank()) {
                    String hook = "Wait! Stop scrolling right now! If you struggle with daily frustration, this one gadget changes everything in 3 seconds.";
                    String cta = "Tap the link below before the 50% flash discount expires tonight!";
                    String revisedPrompt = cleanBase + " | [AI_REFINED_DIRECTIVE: " + aiDiagnosis.replaceAll("\\s+", " ") + "]";
                    return new RefinedScript(hook, cta, revisedPrompt);
                }
            } catch (Exception e) {
                log.warn("[CreativeAutonomousAgent] AI prompt self-refine fallback to heuristic rules: {}", e.getMessage());
            }
        }

        // 2. 确定性领域启发式规则兜底
        String hook;
        String cta;

        switch (issue) {
            case HOOK_FATIGUE -> {
                hook = "Wait! Stop scrolling right now! If you struggle with daily frustration, this one gadget changes everything in 3 seconds.";
                cta = "Tap the link below before the 50% flash discount expires tonight!";
            }
            case CTA_INEFFECTIVE -> {
                hook = "Everyone is talking about this viral secret...";
                cta = "EXCLUSIVE DEAL: Only 37 units left at this price! Claim your VIP discount package right now before it's gone!";
            }
            case COST_OVERRUN -> {
                hook = "Why pay $200 when you can get the exact same premium quality for under $30?";
                cta = "Try it 100% risk-free with a 30-day money-back guarantee. Order yours today!";
            }
            case AUDIENCE_FATIGUE -> {
                hook = "You've never seen it from this perspective before - the game changer of 2026.";
                cta = "Join over 100,000 satisfied users. Click install to start your free trial!";
            }
            default -> {
                hook = "Here is the ultimate solution you've been searching for.";
                cta = "Limited time offer - click here to get yours now!";
            }
        }

        String revisedPrompt = String.format(
                "%s | [REFINED_HOOK: %s] | [HIGH_CONVERTING_CTA: %s] | VisualStyle: Dynamic Cinematic Lighting, 4k Ultra-Sharp, Fast Paced Cuts",
                cleanBase, hook, cta
        );

        return new RefinedScript(hook, cta, revisedPrompt);
    }

    /**
     * 生成营销文案多维变体（标题、钩子、行动指令）
     */
    public String generateCopyVariants(
            String tenantId,
            String productName,
            String targetAudience,
            String sellingPoints,
            String callToAction
    ) {
        if (aiOrchestratorService != null) {
            return aiOrchestratorService.generateAdCreativeCopy(
                    tenantId, productName, targetAudience, sellingPoints, callToAction
            );
        }
        return "High converting ad copy for " + productName + ": Special discount for " + targetAudience;
    }

    private boolean isRateLimited(String materialId) {
        if (materialId == null) return false;
        List<Instant> timestamps = materialOptimizationTimestamps.get(materialId);
        if (timestamps == null || timestamps.isEmpty()) {
            return false;
        }
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        long recentCount = timestamps.stream().filter(t -> t.isAfter(cutoff)).count();
        return recentCount >= MAX_DAILY_OPTIMIZATIONS;
    }

    private void recordOptimizationTimestamp(String materialId) {
        if (materialId == null) return;
        materialOptimizationTimestamps.compute(materialId, (k, list) -> {
            List<Instant> updated = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
            updated.add(Instant.now());
            // 清理 24 小时之前的旧记录
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            updated.removeIf(t -> t.isBefore(cutoff));
            return updated;
        });
    }

    public List<CreativeAgentDiagnosis> getDiagnosisHistory(String materialId) {
        List<CreativeAgentDiagnosis> list = diagnosisHistory.get(materialId);
        return list != null ? new ArrayList<>(list) : List.of();
    }

    public Optional<CreativeAgentAction> getAction(String actionId) {
        return Optional.ofNullable(actionStore.get(actionId));
    }

    public void clear() {
        diagnosisHistory.clear();
        actionStore.clear();
        materialOptimizationTimestamps.clear();
    }

    public record RefinedScript(
            String openingHook,
            String closingCta,
            String fullRevisedPrompt
    ) {}
}
