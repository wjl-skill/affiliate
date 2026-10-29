package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 分布式 LinUCB 多臂老虎机模型参数持久化实体 (Bandit Arm Entity)
 * <p>
 * 映射数据库表 `affiliate_bandit_arm`。
 */
@TableName("affiliate_bandit_arm")
public class AffiliateBanditArmEntity {

    @TableId(type = IdType.INPUT)
    private String armKey;
    private String tenantId;
    private String smartLinkId;
    private String offerId;
    private Integer dimension;
    private String aInvJson;
    private String bVecJson;
    private Integer sampleCount;
    private Integer clicks;
    private Integer conversions;
    private BigDecimal totalReward;
    private Instant updatedAt;

    public AffiliateBanditArmEntity() {}

    public AffiliateBanditArmEntity(String armKey, String tenantId, String smartLinkId, String offerId,
                                    Integer dimension, String aInvJson, String bVecJson,
                                    Integer sampleCount, Integer clicks, Integer conversions,
                                    BigDecimal totalReward, Instant updatedAt) {
        this.armKey = armKey;
        this.tenantId = tenantId;
        this.smartLinkId = smartLinkId;
        this.offerId = offerId;
        this.dimension = dimension;
        this.aInvJson = aInvJson;
        this.bVecJson = bVecJson;
        this.sampleCount = sampleCount;
        this.clicks = clicks;
        this.conversions = conversions;
        this.totalReward = totalReward;
        this.updatedAt = updatedAt;
    }

    public String getArmKey() { return armKey; }
    public void setArmKey(String armKey) { this.armKey = armKey; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getSmartLinkId() { return smartLinkId; }
    public void setSmartLinkId(String smartLinkId) { this.smartLinkId = smartLinkId; }

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public Integer getDimension() { return dimension; }
    public void setDimension(Integer dimension) { this.dimension = dimension; }

    public String getAInvJson() { return aInvJson; }
    public void setAInvJson(String aInvJson) { this.aInvJson = aInvJson; }

    public String getBVecJson() { return bVecJson; }
    public void setBVecJson(String bVecJson) { this.bVecJson = bVecJson; }

    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }

    public Integer getClicks() { return clicks; }
    public void setClicks(Integer clicks) { this.clicks = clicks; }

    public Integer getConversions() { return conversions; }
    public void setConversions(Integer conversions) { this.conversions = conversions; }

    public BigDecimal getTotalReward() { return totalReward; }
    public void setTotalReward(BigDecimal totalReward) { this.totalReward = totalReward; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
