package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日报表多维聚合统计持久化实体 (ReportDaily MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `report_daily`。
 */
@TableName("report_daily")
public class ReportDailyEntity {

    private String tenantId;

    private LocalDate reportDate;

    private String campaignId;

    private Long impressions;

    private Long clicks;

    private Long conversions;

    private BigDecimal spend;

    private BigDecimal revenue;

    public ReportDailyEntity() {}

    public ReportDailyEntity(String tenantId, LocalDate reportDate, String campaignId,
                             Long impressions, Long clicks, Long conversions, BigDecimal spend, BigDecimal revenue) {
        this.tenantId = tenantId;
        this.reportDate = reportDate;
        this.campaignId = campaignId;
        this.impressions = impressions;
        this.clicks = clicks;
        this.conversions = conversions;
        this.spend = spend;
        this.revenue = revenue;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public Long getImpressions() { return impressions; }
    public void setImpressions(Long impressions) { this.impressions = impressions; }

    public Long getClicks() { return clicks; }
    public void setClicks(Long clicks) { this.clicks = clicks; }

    public Long getConversions() { return conversions; }
    public void setConversions(Long conversions) { this.conversions = conversions; }

    public BigDecimal getSpend() { return spend; }
    public void setSpend(BigDecimal spend) { this.spend = spend; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
}
