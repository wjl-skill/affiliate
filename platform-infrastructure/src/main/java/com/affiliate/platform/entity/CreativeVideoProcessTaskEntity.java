package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 视频智能剪辑与混剪任务实体
 * <p>
 * 映射数据库表 `creative_video_process_task`。
 */
@TableName("creative_video_process_task")
public class CreativeVideoProcessTaskEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String taskNo;
    private String processType;
    private String sourceVideoUrl;
    private String introVideoUrl;
    private Integer targetWidth;
    private Integer targetHeight;
    private String alignMode;
    private String status;
    private String outputVideoUrl;
    private String outputCoverUrl;
    private Double durationSeconds;
    private Long fileSizeBytes;
    private String errorMessage;

    @TableField("operator_params_json")
    private String operatorParamsJson;

    private Instant createdAt;
    private Instant finishedAt;

    public CreativeVideoProcessTaskEntity() {}

    public CreativeVideoProcessTaskEntity(String id, String tenantId, String taskNo, String processType,
                                          String sourceVideoUrl, String introVideoUrl, Integer targetWidth,
                                          Integer targetHeight, String alignMode, String status,
                                          String outputVideoUrl, String outputCoverUrl, Double durationSeconds,
                                          Long fileSizeBytes, String errorMessage, String operatorParamsJson,
                                          Instant createdAt, Instant finishedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.taskNo = taskNo;
        this.processType = processType;
        this.sourceVideoUrl = sourceVideoUrl;
        this.introVideoUrl = introVideoUrl;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.alignMode = alignMode;
        this.status = status;
        this.outputVideoUrl = outputVideoUrl;
        this.outputCoverUrl = outputCoverUrl;
        this.durationSeconds = durationSeconds;
        this.fileSizeBytes = fileSizeBytes;
        this.errorMessage = errorMessage;
        this.operatorParamsJson = operatorParamsJson;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }

    public String getProcessType() { return processType; }
    public void setProcessType(String processType) { this.processType = processType; }

    public String getSourceVideoUrl() { return sourceVideoUrl; }
    public void setSourceVideoUrl(String sourceVideoUrl) { this.sourceVideoUrl = sourceVideoUrl; }

    public String getIntroVideoUrl() { return introVideoUrl; }
    public void setIntroVideoUrl(String introVideoUrl) { this.introVideoUrl = introVideoUrl; }

    public Integer getTargetWidth() { return targetWidth; }
    public void setTargetWidth(Integer targetWidth) { this.targetWidth = targetWidth; }

    public Integer getTargetHeight() { return targetHeight; }
    public void setTargetHeight(Integer targetHeight) { this.targetHeight = targetHeight; }

    public String getAlignMode() { return alignMode; }
    public void setAlignMode(String alignMode) { this.alignMode = alignMode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOutputVideoUrl() { return outputVideoUrl; }
    public void setOutputVideoUrl(String outputVideoUrl) { this.outputVideoUrl = outputVideoUrl; }

    public String getOutputCoverUrl() { return outputCoverUrl; }
    public void setOutputCoverUrl(String outputCoverUrl) { this.outputCoverUrl = outputCoverUrl; }

    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getOperatorParamsJson() { return operatorParamsJson; }
    public void setOperatorParamsJson(String operatorParamsJson) { this.operatorParamsJson = operatorParamsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
