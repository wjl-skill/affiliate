package com.affiliate.platform.service.agent;

import com.affiliate.platform.ai.service.AiOrchestratorService;
import com.affiliate.platform.domain.agent.CreativeAgentAction;
import com.affiliate.platform.domain.agent.CreativeAgentDiagnosis;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.studio.CapCutDraftExportResult;
import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.entity.CreativeAgentActionEntity;
import com.affiliate.platform.mapper.CreativeAgentActionMapper;
import com.affiliate.platform.service.ad.AdLearningRuleEngine;
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
 * 5. 自动向 AdLearningRuleEngine 注册探索期小额预算 AB 实验规则，并持久化至 creative_agent_action 表；
 * 6. 防失控熔断机制 (Runaway Guardrail)：单租户单素材 24 小时内最多允许 3 次自治迭代。
 */
@Service
public class CreativeAutonomousAgentService {

    private static final Logger log = LoggerFactory.getLogger(CreativeAutonomousAgentService.class);

    // 24 小时内最大自治迭代次数
    public static final int MAX_DAILY_OPTIMIZATIONS = 3;

    private final StudioProjectService studioProjectService;
    private final AiOrchestratorService aiOrchestratorService;
    private final AdLearningRuleEngine adLearningRuleEngine;
    private final CreativeAgentActionMapper actionMapper;

    // 历史诊断记录：materialId -> 诊断历史
    private final ConcurrentMap<String, List<CreativeAgentDiagnosis>> diagnosisHistory = new ConcurrentHashMap<>();
    // 历史调优执行流水：actionId -> Action
    private final ConcurrentMap<String, CreativeAgentAction> actionStore = new ConcurrentHashMap<>();
    // 租户+素材维度的优化时间戳记录 (用于频控)："tenantId:materialId" -> [Timestamps]
    private final ConcurrentMap<String, List<Instant>> materialOptimizationTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public CreativeAutonomousAgentService(
            @Autowired(required = false) StudioProjectService studioProjectService,
            @Autowired(required = false) AiOrchestratorService aiOrchestratorService,
            @Autowired(required = false) AdLearningRuleEngine adLearningRuleEngine,
            @Autowired(required = false) CreativeAgentActionMapper actionMapper
    ) {
        this.studioProjectService = studioProjectService;
        this.aiOrchestratorService = aiOrchestratorService;
        this.adLearningRuleEngine = adLearningRuleEngine;
        this.actionMapper = actionMapper;
        preloadFromDatabase();
    }

    public CreativeAutonomousAgentService(
            StudioProjectService studioProjectService,
            AiOrchestratorService aiOrchestratorService
    ) {
        this(studioProjectService, aiOrchestratorService, null, null);
    }

    public CreativeAutonomousAgentService(StudioProjectService studioProjectService) {
        this(studioProjectService, null, null, null);
    }

    public CreativeAutonomousAgentService() {
        this(null, null, null, null);
    }

    private void preloadFromDatabase() {
        if (actionMapper != null) {
            try {
                List<CreativeAgentActionEntity> entities = actionMapper.selectList(null);
                if (entities != null) {
                    for (CreativeAgentActionEntity e : entities) {
                        CreativeAgentAction.ActionStatus status;
                        try {
                            status = CreativeAgentAction.ActionStatus.valueOf(e.getStatus());
                        } catch (Exception ex) {
                            status = CreativeAgentAction.ActionStatus.DIAGNOSED;
                        }
                        CreativeAgentAction action = new CreativeAgentAction(
                                e.getActionId(),
                                e.getTenantId(),
                                e.getMaterialId(),
                                e.getOriginalProjectId(),
                                e.getNewProjectId(),
                                e.getRevisedPrompt(),
                                e.getRefinedOpeningHook(),
                                e.getRefinedCta(),
                                e.getCapcutDraftPath(),
                                status,
                                e.getExecutionLog(),
                                e.getCreatedAt() != null ? e.getCreatedAt() : Instant.now()
                        );
                        actionStore.put(action.actionId(), action);
                    }
                }
            } catch (Exception e) {
                log.debug("[CreativeAutonomousAgentService] DB preload skipped: {}", e.getMessage());
            }
        }
    }

    /**
     * 对低质或衰退素材执行端到端自治诊断与闭环重构
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
        String rateLimitKey = effectiveTenant + ":" + materialId;

        // 1. 执行防失控频控检查 (Rate Limiting Check)
        if (isRateLimited(rateLimitKey)) {
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
            persistAction(limitedAction);
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
        recordOptimizationTimestamp(rateLimitKey);

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
                        "ark_seedance_2_0"
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
                        logBuilder.append("已自动生成新工程 ").append(newProjectId).append(" 并成功导出剪映 4 轨道草稿包; ");
                    } else {
                        finalStatus = CreativeAgentAction.ActionStatus.PROJECT_REGENERATED;
                        logBuilder.append("新工程已生成，草稿导出降级; ");
                    }

                    // 5. 自动注入广告学习期小额 AB 实验规则
                    if (adLearningRuleEngine != null) {
                        adLearningRuleEngine.registerLearningExperiment(
                                effectiveTenant,
                                newProjectId,
                                "AutoAB_" + materialId,
                                "TIKTOK"
                        );
                        logBuilder.append("已注入学习期 AB 探索规则");
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

        persistAction(action);
        log.info("Creative agent successfully executed autonomous action: id={}, materialId={}, status={}",
                actionId, materialId, finalStatus);
        return action;
    }

    private void persistAction(CreativeAgentAction action) {
        actionStore.put(action.actionId(), action);
        if (actionMapper != null) {
            try {
                CreativeAgentActionEntity entity = new CreativeAgentActionEntity(
                        null,
                        action.actionId(),
                        action.tenantId(),
                        action.materialId(),
                        action.originalProjectId(),
                        action.newProjectId(),
                        action.revisedPrompt(),
                        action.refinedOpeningHook(),
                        action.refinedCta(),
                        action.capCutDraftPath(),
                        action.status() != null ? action.status().name() : "DIAGNOSED",
                        action.executionLog(),
                        action.createdAt() != null ? action.createdAt() : Instant.now()
                );
                actionMapper.insert(entity);
            } catch (Exception e) {
                log.debug("[CreativeAutonomousAgentService] Persist action skipped: {}", e.getMessage());
            }
        }
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
        } else if (roi != null && roi.compareTo(BigDecimal.valueOf(0.60)) < 0 && dropOff3s < 0.50) {
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

        // 1. 计算启发式基线 Hook & CTA
        String heuristicHook;
        String heuristicCta;
        switch (issue != null ? issue : CreativeAgentDiagnosis.DiagnosisType.UNKNOWN_DECLINE) {
            case HOOK_FATIGUE -> {
                heuristicHook = "Wait! Stop scrolling right now! If you struggle with daily frustration, this one gadget changes everything in 3 seconds.";
                heuristicCta = "Tap the link below before the 50% flash discount expires tonight!";
            }
            case CTA_INEFFECTIVE -> {
                heuristicHook = "Everyone is talking about this viral secret...";
                heuristicCta = "EXCLUSIVE DEAL: Only 37 units left at this price! Claim your VIP discount package right now before it's gone!";
            }
            case COST_OVERRUN -> {
                heuristicHook = "Why pay $200 when you can get the exact same premium quality for under $30?";
                heuristicCta = "Try it 100% risk-free with a 30-day money-back guarantee. Order yours today!";
            }
            case AUDIENCE_FATIGUE -> {
                heuristicHook = "You've never seen it from this perspective before - the game changer of 2026.";
                heuristicCta = "Join over 100,000 satisfied users. Click install to start your free trial!";
            }
            default -> {
                heuristicHook = "Here is the ultimate solution you've been searching for.";
                heuristicCta = "Limited time offer - click here to get yours now!";
            }
        }

        // 2. 优先尝试大模型自主推理自省重构
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
                    String compactAi = aiDiagnosis.replaceAll("\\s+", " ").trim();
                    String hook = heuristicHook + " [" + compactAi.substring(0, Math.min(80, compactAi.length())) + "]";
                    String cta = heuristicCta;
                    String revisedPrompt = cleanBase + " | [REFINED_HOOK: " + hook + "] | [HIGH_CONVERTING_CTA: " + cta + "] | [AI_REFINED_DIRECTIVE: " + compactAi + "]";
                    return new RefinedScript(hook, cta, revisedPrompt);
                }
            } catch (Exception e) {
                log.warn("[CreativeAutonomousAgent] AI prompt self-refine fallback to heuristic rules: {}", e.getMessage());
            }
        }

        String revisedPrompt = String.format(
                "%s | [REFINED_HOOK: %s] | [HIGH_CONVERTING_CTA: %s] | VisualStyle: Dynamic Cinematic Lighting, 4k Ultra-Sharp, Fast Paced Cuts",
                cleanBase, heuristicHook, heuristicCta
        );

        return new RefinedScript(heuristicHook, heuristicCta, revisedPrompt);
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

    private boolean isRateLimited(String rateLimitKey) {
        if (rateLimitKey == null) return false;
        List<Instant> timestamps = materialOptimizationTimestamps.get(rateLimitKey);
        if (timestamps == null || timestamps.isEmpty()) {
            return false;
        }
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        long recentCount = timestamps.stream().filter(t -> t.isAfter(cutoff)).count();
        return recentCount >= MAX_DAILY_OPTIMIZATIONS;
    }

    private void recordOptimizationTimestamp(String rateLimitKey) {
        if (rateLimitKey == null) return;
        materialOptimizationTimestamps.compute(rateLimitKey, (k, list) -> {
            List<Instant> updated = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
            updated.add(Instant.now());
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            updated.removeIf(t -> t.isBefore(cutoff));
            return updated;
        });
    }

    public List<CreativeAgentDiagnosis> getDiagnosisHistory(String materialId) {
        List<CreativeAgentDiagnosis> list = diagnosisHistory.get(materialId);
        return list != null ? new ArrayList<>(list) : List.of();
    }

    public List<CreativeAgentDiagnosis> getDiagnosisHistory(String tenantId, String materialId) {
        List<CreativeAgentDiagnosis> list = getDiagnosisHistory(materialId);
        if (tenantId == null || tenantId.isBlank()) {
            return list;
        }
        return list.stream()
                .filter(d -> tenantId.equals(d.tenantId()) || "default".equals(d.tenantId()))
                .toList();
    }

    public Optional<CreativeAgentAction> getAction(String actionId) {
        return Optional.ofNullable(actionStore.get(actionId));
    }

    public List<CreativeAgentAction> listActions(String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        return actionStore.values().stream()
                .filter(a -> effectiveTenant.equals(a.tenantId())
                        || ("default".equals(effectiveTenant) && "public".equals(a.tenantId()))
                        || ("public".equals(effectiveTenant) && "default".equals(a.tenantId())))
                .sorted(Comparator.comparing(CreativeAgentAction::createdAt).reversed())
                .toList();
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
