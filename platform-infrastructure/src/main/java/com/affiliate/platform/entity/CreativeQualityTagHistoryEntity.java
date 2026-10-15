package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

/**
 * 素材质量标签评估快照历史实体
 * <p>
 * 映射数据库表 `creative_quality_tag_history`。
 */
@TableName("creative_quality_tag_history")
public class CreativeQualityTagHistoryEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String creativeId;
    private LocalDate statDate;
    private String qualityLevel;
    private BigDecimal score;
    @TableField("promotion_cost_7d")
    private BigDecimal promotionCost7d;

    @TableField("clicks_7d")
    private Long clicks7d;

    @TableField("installs_7d")
    private Long installs7d;

    @TableField("roas_7d")
    private BigDecimal roas7d;

    @TableField("cpi_7d")
    private BigDecimal cpi7d;

    @TableField("ctr_7d")
    private BigDecimal ctr7d;

    @TableField("reason_tags")
    private String reasonTags;

    private String benchmarkId;
    private Instant createdAt;

    public CreativeQualityTagHistoryEntity() {}

    public CreativeQualityTagHistoryEntity(String id, String tenantId, String creativeId, LocalDate statDate,
                                           String qualityLevel, BigDecimal score, BigDecimal promotionCost7d,
                                           Long clicks7d, Long installs7d, BigDecimal roas7d, BigDecimal cpi7d,
                                           BigDecimal ctr7d, String reasonTags, String benchmarkId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.creativeId = creativeId;
        this.statDate = statDate;
        this.qualityLevel = qualityLevel;
        this.score = score;
        this.promotionCost7d = promotionCost7d;
        this.clicks7d = clicks7d;
        this.installs7d = installs7d;
        this.roas7d = roas7d;
        this.cpi7d = cpi7d;
        this.ctr7d = ctr7d;
        this.reasonTags = reasonTags;
        this.benchmarkId = benchmarkId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCreativeId() { return creativeId; }
    public void setCreativeId(String creativeId) { this.creativeId = creativeId; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public String getQualityLevel() { return qualityLevel; }
    public void setQualityLevel(String qualityLevel) { this.qualityLevel = qualityLevel; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getPromotionCost7d() { return promotionCost7d; }
    public void setPromotionCost7d(BigDecimal promotionCost7d) { this.promotionCost7d = promotionCost7d; }

    public Long getClicks7d() { return clicks7d; }
    public void setClicks7d(Long clicks7d) { this.clicks7d = clicks7d; }

    public Long getInstalls7d() { return installs7d; }
    public void setInstalls7d(Long installs7d) { this.installs7d = installs7d; }

    public BigDecimal getRoas7d() { return roas7d; }
    public void setRoas7d(BigDecimal roas7d) { this.roas7d = roas7d; }

    public BigDecimal getCpi7d() { return cpi7d; }
    public void setCpi7d(BigDecimal cpi7d) { this.cpi7d = cpi7d; }

    public BigDecimal getCtr7d() { return ctr7d; }
    public void setCtr7d(BigDecimal ctr7d) { this.ctr7d = ctr7d; }

    public String getReasonTags() { return reasonTags; }
    public void setReasonTags(String reasonTags) { this.reasonTags = reasonTags; }

    public String getBenchmarkId() { return benchmarkId; }
    public void setBenchmarkId(String benchmarkId) { this.benchmarkId = benchmarkId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
