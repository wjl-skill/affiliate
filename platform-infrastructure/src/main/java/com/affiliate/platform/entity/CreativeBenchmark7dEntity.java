package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

/**
 * 7日大盘基准线快照实体
 * <p>
 * 映射数据库表 `creative_benchmark_7d`。
 */
@TableName("creative_benchmark_7d")
public class CreativeBenchmark7dEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private LocalDate statDate;
    private LocalDate windowStartDate;
    private LocalDate windowEndDate;
    private Integer materialSampleCount;
    private BigDecimal sampleCostThreshold;
    private Long sampleClickThreshold;
    private Long sampleInstallThreshold;
    private BigDecimal roasP75;
    private BigDecimal roasP25;
    private BigDecimal costP50;
    private BigDecimal cpiAvg;
    private BigDecimal ctrAvg;
    private BigDecimal cvrInstallAvg;
    private Instant createdAt;

    public CreativeBenchmark7dEntity() {}

    public CreativeBenchmark7dEntity(String id, String tenantId, LocalDate statDate,
                                     LocalDate windowStartDate, LocalDate windowEndDate,
                                     Integer materialSampleCount, BigDecimal sampleCostThreshold,
                                     Long sampleClickThreshold, Long sampleInstallThreshold,
                                     BigDecimal roasP75, BigDecimal roasP25, BigDecimal costP50,
                                     BigDecimal cpiAvg, BigDecimal ctrAvg, BigDecimal cvrInstallAvg,
                                     Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.statDate = statDate;
        this.windowStartDate = windowStartDate;
        this.windowEndDate = windowEndDate;
        this.materialSampleCount = materialSampleCount;
        this.sampleCostThreshold = sampleCostThreshold;
        this.sampleClickThreshold = sampleClickThreshold;
        this.sampleInstallThreshold = sampleInstallThreshold;
        this.roasP75 = roasP75;
        this.roasP25 = roasP25;
        this.costP50 = costP50;
        this.cpiAvg = cpiAvg;
        this.ctrAvg = ctrAvg;
        this.cvrInstallAvg = cvrInstallAvg;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public LocalDate getWindowStartDate() { return windowStartDate; }
    public void setWindowStartDate(LocalDate windowStartDate) { this.windowStartDate = windowStartDate; }

    public LocalDate getWindowEndDate() { return windowEndDate; }
    public void setWindowEndDate(LocalDate windowEndDate) { this.windowEndDate = windowEndDate; }

    public Integer getMaterialSampleCount() { return materialSampleCount; }
    public void setMaterialSampleCount(Integer materialSampleCount) { this.materialSampleCount = materialSampleCount; }

    public BigDecimal getSampleCostThreshold() { return sampleCostThreshold; }
    public void setSampleCostThreshold(BigDecimal sampleCostThreshold) { this.sampleCostThreshold = sampleCostThreshold; }

    public Long getSampleClickThreshold() { return sampleClickThreshold; }
    public void setSampleClickThreshold(Long sampleClickThreshold) { this.sampleClickThreshold = sampleClickThreshold; }

    public Long getSampleInstallThreshold() { return sampleInstallThreshold; }
    public void setSampleInstallThreshold(Long sampleInstallThreshold) { this.sampleInstallThreshold = sampleInstallThreshold; }

    public BigDecimal getRoasP75() { return roasP75; }
    public void setRoasP75(BigDecimal roasP75) { this.roasP75 = roasP75; }

    public BigDecimal getRoasP25() { return roasP25; }
    public void setRoasP25(BigDecimal roasP25) { this.roasP25 = roasP25; }

    public BigDecimal getCostP50() { return costP50; }
    public void setCostP50(BigDecimal costP50) { this.costP50 = costP50; }

    public BigDecimal getCpiAvg() { return cpiAvg; }
    public void setCpiAvg(BigDecimal cpiAvg) { this.cpiAvg = cpiAvg; }

    public BigDecimal getCtrAvg() { return ctrAvg; }
    public void setCtrAvg(BigDecimal ctrAvg) { this.ctrAvg = ctrAvg; }

    public BigDecimal getCvrInstallAvg() { return cvrInstallAvg; }
    public void setCvrInstallAvg(BigDecimal cvrInstallAvg) { this.cvrInstallAvg = cvrInstallAvg; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
