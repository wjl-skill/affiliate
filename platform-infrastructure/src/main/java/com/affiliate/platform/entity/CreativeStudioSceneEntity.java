package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * AI 工作台分镜实体
 * <p>
 * 映射数据库表 `creative_studio_scene`。
 */
@TableName("creative_studio_scene")
public class CreativeStudioSceneEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String projectId;
    private Integer sceneIndex;
    private Integer durationSeconds;
    private String shotPrompt;
    private String voiceoverText;
    private String voiceoverTone;
    private String voiceoverSpeed;
    private String subtitleText;

    @TableField("subtitle_keywords")
    private String subtitleKeywords;

    private String bgmCue;
    private String transitionType;
    private String transitionDesc;
    private String startKeyframeUrl;
    private String endKeyframeUrl;
    private String videoClipUrl;
    private String taskId;
    private String status;
    private Instant createdAt;

    public CreativeStudioSceneEntity() {}

    public CreativeStudioSceneEntity(String id, String tenantId, String projectId, Integer sceneIndex,
                                     Integer durationSeconds, String shotPrompt, String voiceoverText,
                                     String voiceoverTone, String voiceoverSpeed, String subtitleText,
                                     String subtitleKeywords, String bgmCue, String transitionType,
                                     String transitionDesc, String startKeyframeUrl, String endKeyframeUrl,
                                     String videoClipUrl, String taskId, String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.sceneIndex = sceneIndex;
        this.durationSeconds = durationSeconds;
        this.shotPrompt = shotPrompt;
        this.voiceoverText = voiceoverText;
        this.voiceoverTone = voiceoverTone;
        this.voiceoverSpeed = voiceoverSpeed;
        this.subtitleText = subtitleText;
        this.subtitleKeywords = subtitleKeywords;
        this.bgmCue = bgmCue;
        this.transitionType = transitionType;
        this.transitionDesc = transitionDesc;
        this.startKeyframeUrl = startKeyframeUrl;
        this.endKeyframeUrl = endKeyframeUrl;
        this.videoClipUrl = videoClipUrl;
        this.taskId = taskId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Integer getSceneIndex() { return sceneIndex; }
    public void setSceneIndex(Integer sceneIndex) { this.sceneIndex = sceneIndex; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getShotPrompt() { return shotPrompt; }
    public void setShotPrompt(String shotPrompt) { this.shotPrompt = shotPrompt; }

    public String getVoiceoverText() { return voiceoverText; }
    public void setVoiceoverText(String voiceoverText) { this.voiceoverText = voiceoverText; }

    public String getVoiceoverTone() { return voiceoverTone; }
    public void setVoiceoverTone(String voiceoverTone) { this.voiceoverTone = voiceoverTone; }

    public String getVoiceoverSpeed() { return voiceoverSpeed; }
    public void setVoiceoverSpeed(String voiceoverSpeed) { this.voiceoverSpeed = voiceoverSpeed; }

    public String getSubtitleText() { return subtitleText; }
    public void setSubtitleText(String subtitleText) { this.subtitleText = subtitleText; }

    public String getSubtitleKeywords() { return subtitleKeywords; }
    public void setSubtitleKeywords(String subtitleKeywords) { this.subtitleKeywords = subtitleKeywords; }

    public String getBgmCue() { return bgmCue; }
    public void setBgmCue(String bgmCue) { this.bgmCue = bgmCue; }

    public String getTransitionType() { return transitionType; }
    public void setTransitionType(String transitionType) { this.transitionType = transitionType; }

    public String getTransitionDesc() { return transitionDesc; }
    public void setTransitionDesc(String transitionDesc) { this.transitionDesc = transitionDesc; }

    public String getStartKeyframeUrl() { return startKeyframeUrl; }
    public void setStartKeyframeUrl(String startKeyframeUrl) { this.startKeyframeUrl = startKeyframeUrl; }

    public String getEndKeyframeUrl() { return endKeyframeUrl; }
    public void setEndKeyframeUrl(String endKeyframeUrl) { this.endKeyframeUrl = endKeyframeUrl; }

    public String getVideoClipUrl() { return videoClipUrl; }
    public void setVideoClipUrl(String videoClipUrl) { this.videoClipUrl = videoClipUrl; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
