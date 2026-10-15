package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 自动化批量混剪配置实体
 * <p>
 * 映射数据库表 `creative_auto_stitch_config`。
 */
@TableName("creative_auto_stitch_config")
public class CreativeAutoStitchConfigEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String configName;

    @TableField("folder_sources")
    private String folderSources;

    private String targetAspectRatio;
    private String alignMode;

    @TableField("cta_config")
    private String ctaConfig;

    private Instant scheduledTime;
    private String status;
    private Instant createdAt;

    public CreativeAutoStitchConfigEntity() {}

    public CreativeAutoStitchConfigEntity(String id, String tenantId, String configName, String folderSources,
                                          String targetAspectRatio, String alignMode, String ctaConfig,
                                          Instant scheduledTime, String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.configName = configName;
        this.folderSources = folderSources;
        this.targetAspectRatio = targetAspectRatio;
        this.alignMode = alignMode;
        this.ctaConfig = ctaConfig;
        this.scheduledTime = scheduledTime;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }

    public String getFolderSources() { return folderSources; }
    public void setFolderSources(String folderSources) { this.folderSources = folderSources; }

    public String getTargetAspectRatio() { return targetAspectRatio; }
    public void setTargetAspectRatio(String targetAspectRatio) { this.targetAspectRatio = targetAspectRatio; }

    public String getAlignMode() { return alignMode; }
    public void setAlignMode(String alignMode) { this.alignMode = alignMode; }

    public String getCtaConfig() { return ctaConfig; }
    public void setCtaConfig(String ctaConfig) { this.ctaConfig = ctaConfig; }

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
