package com.affiliate.platform.domain.agent;

import java.time.Instant;

/**
 * 营销创意自治 Agent 调优执行记录 (Creative Autonomous Agent Action)
 * <p>
 * 跟踪 Agent 从诊断、分镜与提示词 Self-Refine、长视频工程重新生成直至 CapCut 草稿导出的完整闭环。
 */
public record CreativeAgentAction(
        String actionId,
        String tenantId,
        String materialId,
        String originalProjectId,
        String newProjectId,
        String revisedPrompt,
        String refinedOpeningHook,
        String refinedCta,
        String capCutDraftPath,
        ActionStatus status,
        String executionLog,
        Instant createdAt
) {
    public CreativeAgentAction {
        tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        status = status != null ? status : ActionStatus.DIAGNOSED;
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public enum ActionStatus {
        /** 已完成诊断 */
        DIAGNOSED,
        /** 剧本分镜已优化重写 (Prompt Self-Refined) */
        SCRIPT_REFINED,
        /** 长视频工程已重新派发生效 (Project Regenerated) */
        PROJECT_REGENERATED,
        /** 剪映 4 轨道工程草稿已生成 (CapCut Draft Exported) */
        CAPCUT_DRAFTED,
        /** 全链路闭环完成 */
        COMPLETED,
        /** 频次超限熔断保护 (Rate Limited) */
        RATE_LIMITED,
        /** 异常失败 */
        FAILED
    }
}
