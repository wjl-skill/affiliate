package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * AI 长视频项目持久化实体
 */
@TableName("creative_studio_project")
public class CreativeStudioProjectEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String title;
    private String brief;
    private String aspectRatio;
    private Integer durationSeconds;
    private String targetPlatform;
    private String adType;
    private String creativeStyle;
    private String modelCode;
    private String status;
    private String finalVideoUrl;
    private String capcutDraftUrl;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public CreativeStudioProjectEntity() {}

    public CreativeStudioProjectEntity(String id, String tenantId, String title, String brief, String aspectRatio,
                                       Integer durationSeconds, String targetPlatform, String adType,
                                       String creativeStyle, String modelCode, String status, String finalVideoUrl,
                                       String capcutDraftUrl, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.brief = brief;
        this.aspectRatio = aspectRatio;
        this.durationSeconds = durationSeconds;
        this.targetPlatform = targetPlatform;
        this.adType = adType;
        this.creativeStyle = creativeStyle;
        this.modelCode = modelCode;
        this.status = status;
        this.finalVideoUrl = finalVideoUrl;
        this.capcutDraftUrl = capcutDraftUrl;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBrief() { return brief; }
    public void setBrief(String brief) { this.brief = brief; }
    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getTargetPlatform() { return targetPlatform; }
    public void setTargetPlatform(String targetPlatform) { this.targetPlatform = targetPlatform; }
    public String getAdType() { return adType; }
    public void setAdType(String adType) { this.adType = adType; }
    public String getCreativeStyle() { return creativeStyle; }
    public void setCreativeStyle(String creativeStyle) { this.creativeStyle = creativeStyle; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFinalVideoUrl() { return finalVideoUrl; }
    public void setFinalVideoUrl(String finalVideoUrl) { this.finalVideoUrl = finalVideoUrl; }
    public String getCapcutDraftUrl() { return capcutDraftUrl; }
    public void setCapcutDraftUrl(String capcutDraftUrl) { this.capcutDraftUrl = capcutDraftUrl; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
