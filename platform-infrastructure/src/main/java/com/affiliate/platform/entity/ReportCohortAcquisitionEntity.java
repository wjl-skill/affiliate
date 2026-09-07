package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cohort 留存获客支出持久化实体 (Cohort Acquisition MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `report_cohort_acquisition`。
 */
@TableName("report_cohort_acquisition")
public class ReportCohortAcquisitionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String userId;
    private LocalDate cohortDate;
    private BigDecimal cost;
    private Instant createdAt;

    public ReportCohortAcquisitionEntity() {}

    public ReportCohortAcquisitionEntity(String id, String tenantId, String userId,
                                         LocalDate cohortDate, BigDecimal cost, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.cohortDate = cohortDate;
        this.cost = cost;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getCohortDate() { return cohortDate; }
    public void setCohortDate(LocalDate cohortDate) { this.cohortDate = cohortDate; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
