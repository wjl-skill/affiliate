package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Apple SKAdNetwork 4.0 归因回传持久化实体 (Affiliate SKAN Postback Entity)
 * <p>
 * 映射数据库表 `affiliate_skan_postback` (V18)。
 */
@TableName("affiliate_skan_postback")
public class AffiliateSkanPostbackEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String transactionId;
    private String adNetworkId;
    private String sourceIdentifier;
    private String campaignId;
    private Integer postbackSequenceIndex;
    private Integer conversionValueFine;
    private String conversionValueCoarse;
    private BigDecimal estimatedRevenue;
    private Integer fidelityType;
    private String skanVersion;
    private String appId;
    private String attributionSignature;
    private Instant receivedAt;
    private String status;

    public AffiliateSkanPostbackEntity() {
    }

    public AffiliateSkanPostbackEntity(
            Long id,
            String tenantId,
            String transactionId,
            String adNetworkId,
            String sourceIdentifier,
            String campaignId,
            Integer postbackSequenceIndex,
            Integer conversionValueFine,
            String conversionValueCoarse,
            BigDecimal estimatedRevenue,
            Integer fidelityType,
            String skanVersion,
            String appId,
            String attributionSignature,
            Instant receivedAt,
            String status
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.transactionId = transactionId;
        this.adNetworkId = adNetworkId;
        this.sourceIdentifier = sourceIdentifier;
        this.campaignId = campaignId;
        this.postbackSequenceIndex = postbackSequenceIndex;
        this.conversionValueFine = conversionValueFine;
        this.conversionValueCoarse = conversionValueCoarse;
        this.estimatedRevenue = estimatedRevenue;
        this.fidelityType = fidelityType;
        this.skanVersion = skanVersion;
        this.appId = appId;
        this.attributionSignature = attributionSignature;
        this.receivedAt = receivedAt;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getAdNetworkId() { return adNetworkId; }
    public void setAdNetworkId(String adNetworkId) { this.adNetworkId = adNetworkId; }

    public String getSourceIdentifier() { return sourceIdentifier; }
    public void setSourceIdentifier(String sourceIdentifier) { this.sourceIdentifier = sourceIdentifier; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public Integer getPostbackSequenceIndex() { return postbackSequenceIndex; }
    public void setPostbackSequenceIndex(Integer postbackSequenceIndex) { this.postbackSequenceIndex = postbackSequenceIndex; }

    public Integer getConversionValueFine() { return conversionValueFine; }
    public void setConversionValueFine(Integer conversionValueFine) { this.conversionValueFine = conversionValueFine; }

    public String getConversionValueCoarse() { return conversionValueCoarse; }
    public void setConversionValueCoarse(String conversionValueCoarse) { this.conversionValueCoarse = conversionValueCoarse; }

    public BigDecimal getEstimatedRevenue() { return estimatedRevenue; }
    public void setEstimatedRevenue(BigDecimal estimatedRevenue) { this.estimatedRevenue = estimatedRevenue; }

    public Integer getFidelityType() { return fidelityType; }
    public void setFidelityType(Integer fidelityType) { this.fidelityType = fidelityType; }

    public String getSkanVersion() { return skanVersion; }
    public void setSkanVersion(String skanVersion) { this.skanVersion = skanVersion; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAttributionSignature() { return attributionSignature; }
    public void setAttributionSignature(String attributionSignature) { this.attributionSignature = attributionSignature; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
