package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * Offer 补量点击与归因平台 (AppsFlyer / Adjust) 推送日志实体
 * <p>
 * 映射数据库表 `affiliate_offer_supplement_log` (V20)。
 */
@TableName("affiliate_offer_supplement_log")
public class OfferSupplementLogEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String taskBatchId;
    private String ruleId;
    private String offerId;
    private String affiliateId;
    private String clickId;
    private String mmpPlatform;
    private String profileId;
    private String idfa;
    private String gadvid;
    private String ip;
    private String userAgent;
    private String os;
    private String dsPublisherId;
    private String dsAdx;
    private String sourceTag;
    private Integer modelFlag;
    private String targetUrl;
    private Integer httpStatus;
    private Boolean success;
    private String responseSummary;
    private Long latencyMs;
    private Instant createdAt;

    public OfferSupplementLogEntity() {
    }

    public OfferSupplementLogEntity(
            String id,
            String tenantId,
            String taskBatchId,
            String ruleId,
            String offerId,
            String affiliateId,
            String clickId,
            String mmpPlatform,
            String profileId,
            String idfa,
            String gadvid,
            String ip,
            String userAgent,
            String os,
            String dsPublisherId,
            String dsAdx,
            String sourceTag,
            Integer modelFlag,
            String targetUrl,
            Integer httpStatus,
            Boolean success,
            String responseSummary,
            Long latencyMs,
            Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.taskBatchId = taskBatchId;
        this.ruleId = ruleId;
        this.offerId = offerId;
        this.affiliateId = affiliateId;
        this.clickId = clickId;
        this.mmpPlatform = mmpPlatform;
        this.profileId = profileId;
        this.idfa = idfa;
        this.gadvid = gadvid;
        this.ip = ip;
        this.userAgent = userAgent;
        this.os = os;
        this.dsPublisherId = dsPublisherId;
        this.dsAdx = dsAdx;
        this.sourceTag = sourceTag;
        this.modelFlag = modelFlag;
        this.targetUrl = targetUrl;
        this.httpStatus = httpStatus;
        this.success = success;
        this.responseSummary = responseSummary;
        this.latencyMs = latencyMs;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTaskBatchId() { return taskBatchId; }
    public void setTaskBatchId(String taskBatchId) { this.taskBatchId = taskBatchId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getClickId() { return clickId; }
    public void setClickId(String clickId) { this.clickId = clickId; }

    public String getMmpPlatform() { return mmpPlatform; }
    public void setMmpPlatform(String mmpPlatform) { this.mmpPlatform = mmpPlatform; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getIdfa() { return idfa; }
    public void setIdfa(String idfa) { this.idfa = idfa; }

    public String getGadvid() { return gadvid; }
    public void setGadvid(String gadvid) { this.gadvid = gadvid; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }

    public String getDsPublisherId() { return dsPublisherId; }
    public void setDsPublisherId(String dsPublisherId) { this.dsPublisherId = dsPublisherId; }

    public String getDsAdx() { return dsAdx; }
    public void setDsAdx(String dsAdx) { this.dsAdx = dsAdx; }

    public String getSourceTag() { return sourceTag; }
    public void setSourceTag(String sourceTag) { this.sourceTag = sourceTag; }

    public Integer getModelFlag() { return modelFlag; }
    public void setModelFlag(Integer modelFlag) { this.modelFlag = modelFlag; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getResponseSummary() { return responseSummary; }
    public void setResponseSummary(String responseSummary) { this.responseSummary = responseSummary; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
