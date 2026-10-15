package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * AI 长视频资产实体 (产品/人物/场景)
 * <p>
 * 映射数据库表 `creative_studio_asset`。
 */
@TableName("creative_studio_asset")
public class CreativeStudioAssetEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String projectId;
    private String assetType;
    private String name;
    private String description;

    @TableField("prompt_data_json")
    private String promptDataJson;

    private String imageUrl;
    private String status;
    private Instant createdAt;

    public CreativeStudioAssetEntity() {}

    public CreativeStudioAssetEntity(String id, String tenantId, String projectId, String assetType,
                                     String name, String description, String promptDataJson,
                                     String imageUrl, String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.assetType = assetType;
        this.name = name;
        this.description = description;
        this.promptDataJson = promptDataJson;
        this.imageUrl = imageUrl;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPromptDataJson() { return promptDataJson; }
    public void setPromptDataJson(String promptDataJson) { this.promptDataJson = promptDataJson; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
