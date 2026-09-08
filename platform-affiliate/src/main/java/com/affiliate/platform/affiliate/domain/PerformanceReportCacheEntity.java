package com.affiliate.platform.affiliate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 性能报表缓存实体
 * 用于缓存复杂聚合查询结果
 */
@Entity
@Table(name = "affiliate_performance_report_cache", indexes = {
        @Index(name = "idx_report_hash", columnList = "report_hash", unique = true),
        @Index(name = "idx_report_type", columnList = "report_type"),
        @Index(name = "idx_report_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_report_date", columnList = "start_date,end_date"),
        @Index(name = "idx_report_created", columnList = "created_at")
})
public class PerformanceReportCacheEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "report_hash", nullable = false, length = 64, unique = true)
    private String reportHash; // MD5/SHA256 of request parameters

    @Column(name = "report_type", nullable = false, length = 30)
    private String reportType; // PERFORMANCE, TIME_SERIES, FUNNEL, COMPARISON

    @Column(name = "affiliate_id", length = 64)
    private String affiliateId; // null for global reports

    @Column(name = "offer_id", length = 64)
    private String offerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "group_by_dimension", length = 30)
    private String groupByDimension; // DATE, OFFER, AFFILIATE, COUNTRY, DEVICE, SUB1, SUB2, SUB3

    @Column(name = "time_granularity", length = 20)
    private String timeGranularity; // HOUR, DAY, WEEK, MONTH

    @Column(name = "report_data", nullable = false, columnDefinition = "TEXT")
    private String reportData; // JSON serialized report result

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "total_clicks")
    private Long totalClicks;

    @Column(name = "total_conversions")
    private Long totalConversions;

    @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "total_payout", precision = 15, scale = 2)
    private BigDecimal totalPayout;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Constructors
    public PerformanceReportCacheEntity() {
    }

    public PerformanceReportCacheEntity(String id, String reportHash, String reportType,
                                       String affiliateId, String offerId, LocalDate startDate,
                                       LocalDate endDate, String groupByDimension, String timeGranularity,
                                       String reportData, Integer rowCount, Long totalClicks,
                                       Long totalConversions, BigDecimal totalRevenue,
                                       BigDecimal totalPayout, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.reportHash = reportHash;
        this.reportType = reportType;
        this.affiliateId = affiliateId;
        this.offerId = offerId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.groupByDimension = groupByDimension;
        this.timeGranularity = timeGranularity;
        this.reportData = reportData;
        this.rowCount = rowCount;
        this.totalClicks = totalClicks;
        this.totalConversions = totalConversions;
        this.totalRevenue = totalRevenue;
        this.totalPayout = totalPayout;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReportHash() {
        return reportHash;
    }

    public void setReportHash(String reportHash) {
        this.reportHash = reportHash;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getGroupByDimension() {
        return groupByDimension;
    }

    public void setGroupByDimension(String groupByDimension) {
        this.groupByDimension = groupByDimension;
    }

    public String getTimeGranularity() {
        return timeGranularity;
    }

    public void setTimeGranularity(String timeGranularity) {
        this.timeGranularity = timeGranularity;
    }

    public String getReportData() {
        return reportData;
    }

    public void setReportData(String reportData) {
        this.reportData = reportData;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Long getTotalClicks() {
        return totalClicks;
    }

    public void setTotalClicks(Long totalClicks) {
        this.totalClicks = totalClicks;
    }

    public Long getTotalConversions() {
        return totalConversions;
    }

    public void setTotalConversions(Long totalConversions) {
        this.totalConversions = totalConversions;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getTotalPayout() {
        return totalPayout;
    }

    public void setTotalPayout(BigDecimal totalPayout) {
        this.totalPayout = totalPayout;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
