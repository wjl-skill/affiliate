package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 结构化 Prompt 模板持久化实体 (AI Prompt Template Entity)
 */
@TableName("ai_prompt_template")
public class AiPromptTemplateEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String templateKey;
    private String title;
    private String systemPrompt;
    private String userPromptTemplate;
    private String modelPreference;
    private Instant createdAt;
    private Instant updatedAt;

    public AiPromptTemplateEntity() {}

    public AiPromptTemplateEntity(String id, String tenantId, String templateKey, String title,
                                  String systemPrompt, String userPromptTemplate, String modelPreference,
                                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.templateKey = templateKey;
        this.title = title;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
        this.modelPreference = modelPreference;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getUserPromptTemplate() { return userPromptTemplate; }
    public void setUserPromptTemplate(String userPromptTemplate) { this.userPromptTemplate = userPromptTemplate; }

    public String getModelPreference() { return modelPreference; }
    public void setModelPreference(String modelPreference) { this.modelPreference = modelPreference; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
