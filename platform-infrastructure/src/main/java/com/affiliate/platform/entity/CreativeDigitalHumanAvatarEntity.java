package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 数字人形象资产库实体
 * <p>
 * 映射数据库表 `creative_digital_human_avatar`。
 */
@TableName("creative_digital_human_avatar")
public class CreativeDigitalHumanAvatarEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String avatarName;
    private String avatarType;
    private String originalImageUrl;
    private String maskImageUrl;
    private String defaultSpeaker;
    private String status;
    private Instant createdAt;

    public CreativeDigitalHumanAvatarEntity() {}

    public CreativeDigitalHumanAvatarEntity(String id, String tenantId, String avatarName, String avatarType,
                                            String originalImageUrl, String maskImageUrl, String defaultSpeaker,
                                            String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.avatarName = avatarName;
        this.avatarType = avatarType;
        this.originalImageUrl = originalImageUrl;
        this.maskImageUrl = maskImageUrl;
        this.defaultSpeaker = defaultSpeaker;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAvatarName() { return avatarName; }
    public void setAvatarName(String avatarName) { this.avatarName = avatarName; }

    public String getAvatarType() { return avatarType; }
    public void setAvatarType(String avatarType) { this.avatarType = avatarType; }

    public String getOriginalImageUrl() { return originalImageUrl; }
    public void setOriginalImageUrl(String originalImageUrl) { this.originalImageUrl = originalImageUrl; }

    public String getMaskImageUrl() { return maskImageUrl; }
    public void setMaskImageUrl(String maskImageUrl) { this.maskImageUrl = maskImageUrl; }

    public String getDefaultSpeaker() { return defaultSpeaker; }
    public void setDefaultSpeaker(String defaultSpeaker) { this.defaultSpeaker = defaultSpeaker; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
