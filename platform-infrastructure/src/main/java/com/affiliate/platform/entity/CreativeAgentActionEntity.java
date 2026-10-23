package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 营销创意自治 Agent 调优执行流水持久化实体 (Creative Agent Action Entity)
 * <p>
 * 映射数据库表 `creative_agent_action` (V18)。
 */
@TableName("creative_agent_action")
public class CreativeAgentActionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String actionId;
    private String tenantId;
    private String materialId;
    private String originalProjectId;
    private String newProjectId;
    private String revisedPrompt;
    private String refinedOpeningHook;
    private String refinedCta;
    private String capcutDraftPath;
    private String status;
    private String executionLog;
    private Instant createdAt;

    public CreativeAgentActionEntity() {
    }

    public CreativeAgentActionEntity(
            Long id,
            String actionId,
            String tenantId,
            String materialId,
            String originalProjectId,
            String newProjectId,
            String revisedPrompt,
            String refinedOpeningHook,
            String refinedCta,
            String capcutDraftPath,
            String status,
            String executionLog,
            Instant createdAt
    ) {
        this.id = id;
        this.actionId = actionId;
        this.tenantId = tenantId;
        this.materialId = materialId;
        this.originalProjectId = originalProjectId;
        this.newProjectId = newProjectId;
        this.revisedPrompt = revisedPrompt;
        this.refinedOpeningHook = refinedOpeningHook;
        this.refinedCta = refinedCta;
        this.capcutDraftPath = capcutDraftPath;
        this.status = status;
        this.executionLog = executionLog;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getMaterialId() { return materialId; }
    public void setMaterialId(String materialId) { this.materialId = materialId; }

    public String getOriginalProjectId() { return originalProjectId; }
    public void setOriginalProjectId(String originalProjectId) { this.originalProjectId = originalProjectId; }

    public String getNewProjectId() { return newProjectId; }
    public void setNewProjectId(String newProjectId) { this.newProjectId = newProjectId; }

    public String getRevisedPrompt() { return revisedPrompt; }
    public void setRevisedPrompt(String revisedPrompt) { this.revisedPrompt = revisedPrompt; }

    public String getRefinedOpeningHook() { return refinedOpeningHook; }
    public void setRefinedOpeningHook(String refinedOpeningHook) { this.refinedOpeningHook = refinedOpeningHook; }

    public String getRefinedCta() { return refinedCta; }
    public void setRefinedCta(String refinedCta) { this.refinedCta = refinedCta; }

    public String getCapcutDraftPath() { return capcutDraftPath; }
    public void setCapcutDraftPath(String capcutDraftPath) { this.capcutDraftPath = capcutDraftPath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExecutionLog() { return executionLog; }
    public void setExecutionLog(String executionLog) { this.executionLog = executionLog; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
