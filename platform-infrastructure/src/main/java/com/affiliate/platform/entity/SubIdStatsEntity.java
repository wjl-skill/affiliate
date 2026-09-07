package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sub-ID 维度多维统计事实实体 (Sub-ID Stats Entity)
 * <p>
 * 映射数据库表 `affiliate_sub_id_stats`。
 */
@TableName("affiliate_sub_id_stats")
public class SubIdStatsEntity {

    private String tenantId;
    private String affiliateId;
    private String sub1;
    private Long clicks;
    private Long conversions;
    private BigDecimal totalPayout;
    private BigDecimal totalRevenue;
    private BigDecimal epc;
    private BigDecimal crPercent;
    private Instant updatedAt;

    public SubIdStatsEntity() {}

    public SubIdStatsEntity(String tenantId, String affiliateId, String sub1, Long clicks,
                            Long conversions, BigDecimal totalPayout, BigDecimal totalRevenue,
                            BigDecimal epc, BigDecimal crPercent, Instant updatedAt) {
        this.tenantId = tenantId;
        this.affiliateId = affiliateId;
        this.sub1 = sub1;
        this.clicks = clicks;
        this.conversions = conversions;
        this.totalPayout = totalPayout;
        this.totalRevenue = totalRevenue;
        this.epc = epc;
        this.crPercent = crPercent;
        this.updatedAt = updatedAt;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAffiliateId() { return affiliateId; }
    public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }

    public String getSub1() { return sub1; }
    public void setSub1(String sub1) { this.sub1 = sub1; }

    public Long getClicks() { return clicks; }
    public void setClicks(Long clicks) { this.clicks = clicks; }

    public Long getConversions() { return conversions; }
    public void setConversions(Long conversions) { this.conversions = conversions; }

    public BigDecimal getTotalPayout() { return totalPayout; }
    public void setTotalPayout(BigDecimal totalPayout) { this.totalPayout = totalPayout; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public BigDecimal getEpc() { return epc; }
    public void setEpc(BigDecimal epc) { this.epc = epc; }

    public BigDecimal getCrPercent() { return crPercent; }
    public void setCrPercent(BigDecimal crPercent) { this.crPercent = crPercent; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
