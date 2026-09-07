package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cohort 留存回访创收流水持久化实体 (Cohort Activity MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `report_cohort_activity`。
 */
@TableName("report_cohort_activity")
public class ReportCohortActivityEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String userId;
    private LocalDate activityDate;
    private BigDecimal revenue;
    private Instant createdAt;

    public ReportCohortActivityEntity() {}

    public ReportCohortActivityEntity(String id, String tenantId, String userId,
                                      LocalDate activityDate, BigDecimal revenue, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.activityDate = activityDate;
        this.revenue = revenue;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getActivityDate() { return activityDate; }
    public void setActivityDate(LocalDate activityDate) { this.activityDate = activityDate; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
