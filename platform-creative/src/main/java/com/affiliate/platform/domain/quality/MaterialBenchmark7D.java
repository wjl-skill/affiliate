package com.affiliate.platform.domain.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 7日滑动窗口大盘质量基准线 (Material 7-Day Performance Benchmark)
 * <p>
 * 每日凌晨由数仓 ETL 计算并写入，作为全平台素材质量评级的统一动态标尺。
 *
 * @param id                     基准线唯一标识 (如 "bm_20261014")
 * @param tenantId               所属租户标识
 * @param statDate               基准产出统计日期 (通常为当前判定日)
 * @param windowStartDate        滑动计算窗口起始日期 (statDate - 6天)
 * @param windowEndDate          滑动计算窗口截止日期 (statDate)
 * @param materialSampleCount    参与基准统计的有效素材样本总数
 * @param sampleCostThreshold    样本消耗保护门槛 (默认 100.00，最低兜底 20.00)
 * @param sampleClickThreshold   样本点击保护门槛 (默认 50，最低兜底 20)
 * @param sampleInstallThreshold 样本转化/安装保护门槛 (默认 5，最低兜底 0)
 * @param roasP75                全盘素材 ROAS 的 75 分位数 (高回收及格线)
 * @param roasP25                全盘素材 ROAS 的 25 分位数 (低回收警戒线)
 * @param costP50                全盘素材 7 日消耗的中位数 (放量达标分水岭)
 * @param cpiAvg                 全盘素材平均获客成本 (CPI/CPA 水位线)
 * @param ctrAvg                 全盘素材平均点击率 (CTR 前链路水位线)
 * @param cvrInstallAvg          全盘素材平均安装/转化率 (CVR 后链路水位线)
 * @param createdAt              记录创建时间戳
 */
public record MaterialBenchmark7D(
        String id,
        String tenantId,
        LocalDate statDate,
        LocalDate windowStartDate,
        LocalDate windowEndDate,
        int materialSampleCount,
        BigDecimal sampleCostThreshold,
        long sampleClickThreshold,
        long sampleInstallThreshold,
        BigDecimal roasP75,
        BigDecimal roasP25,
        BigDecimal costP50,
        BigDecimal cpiAvg,
        BigDecimal ctrAvg,
        BigDecimal cvrInstallAvg,
        Instant createdAt
) {
    public static final BigDecimal DEFAULT_COST_THRESHOLD = BigDecimal.valueOf(100.00);
    public static final long DEFAULT_CLICK_THRESHOLD = 50L;
    public static final long DEFAULT_INSTALL_THRESHOLD = 5L;

    public static final BigDecimal FALLBACK_COST_THRESHOLD = BigDecimal.valueOf(20.00);
    public static final long FALLBACK_CLICK_THRESHOLD = 20L;
    public static final long FALLBACK_INSTALL_THRESHOLD = 0L;

    public MaterialBenchmark7D {
        sampleCostThreshold = sampleCostThreshold != null ? sampleCostThreshold : DEFAULT_COST_THRESHOLD;
        sampleClickThreshold = sampleClickThreshold > 0 ? sampleClickThreshold : DEFAULT_CLICK_THRESHOLD;
        sampleInstallThreshold = sampleInstallThreshold >= 0 ? sampleInstallThreshold : DEFAULT_INSTALL_THRESHOLD;
        roasP75 = roasP75 != null ? roasP75 : BigDecimal.valueOf(1.80);
        roasP25 = roasP25 != null ? roasP25 : BigDecimal.valueOf(0.90);
        costP50 = costP50 != null ? costP50 : BigDecimal.valueOf(500.00);
        cpiAvg = cpiAvg != null ? cpiAvg : BigDecimal.valueOf(4.00);
        ctrAvg = ctrAvg != null ? ctrAvg : BigDecimal.valueOf(0.025);
        cvrInstallAvg = cvrInstallAvg != null ? cvrInstallAvg : BigDecimal.valueOf(0.10);
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * 创建默认/基线大盘基准快照 (用于系统冷启动或缺省兜底)
     */
    public static MaterialBenchmark7D createDefault(String tenantId, LocalDate date) {
        LocalDate end = date != null ? date : LocalDate.now();
        LocalDate start = end.minusDays(6);
        return new MaterialBenchmark7D(
                "bm_default_" + end,
                tenantId != null ? tenantId : "public",
                end,
                start,
                end,
                100,
                DEFAULT_COST_THRESHOLD,
                DEFAULT_CLICK_THRESHOLD,
                DEFAULT_INSTALL_THRESHOLD,
                BigDecimal.valueOf(1.80),
                BigDecimal.valueOf(0.90),
                BigDecimal.valueOf(800.00),
                BigDecimal.valueOf(4.20),
                BigDecimal.valueOf(0.025),
                BigDecimal.valueOf(0.12),
                Instant.now()
        );
    }
}
