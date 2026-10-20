package com.affiliate.platform.domain.agent;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 营销创意自治 Agent 诊断报告实体 (Creative Autonomous Agent Diagnosis)
 * <p>
 * 记录 Agent 对投放效果表现不佳（衰退、黑洞）素材的深度根因诊断结论。
 */
public record CreativeAgentDiagnosis(
        String materialId,
        String tenantId,
        MaterialQualityLevel currentLevel,
        DiagnosisType primaryIssue,
        String detailedAnalysis,
        double dropOffRate3s,
        double ctr,
        double cvr,
        BigDecimal roi,
        Instant diagnosedAt
) {
    public CreativeAgentDiagnosis {
        tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        diagnosedAt = diagnosedAt != null ? diagnosedAt : Instant.now();
        roi = roi != null ? roi : BigDecimal.ZERO;
    }

    public enum DiagnosisType {
        /** 前 3 秒留存率低：吸睛 Hook 疲倦或黄金开场缺乏冲突悬念 */
        HOOK_FATIGUE,
        /** CTA 引导软弱：中段留存良好，但结尾转化缺乏紧迫感行动号召 */
        CTA_INEFFECTIVE,
        /** 受众审美疲倦：频次过高导致边际效用递减 */
        AUDIENCE_FATIGUE,
        /** 成本击穿：获客成本严重超出 CPA 容忍线 */
        COST_OVERRUN,
        /** 综合效果偏离 */
        UNKNOWN_DECLINE
    }
}
