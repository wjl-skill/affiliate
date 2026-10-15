package com.affiliate.platform.domain.video;

/**
 * 磁盘容量健康与高水位熔断状态
 */
public record DiskHealthStatus(
        long totalSpaceBytes,
        long usableSpaceBytes,
        long usedSpaceBytes,
        double usedPercent,
        boolean isSafe,
        double alertThresholdPercent,
        String statusDescription
) {
    public static DiskHealthStatus evaluate(long totalSpace, long usableSpace, double alertThreshold) {
        long used = Math.max(0, totalSpace - usableSpace);
        double usedRatio = totalSpace > 0 ? (double) used / totalSpace : 0.0;
        double usedPercent = Math.round(usedRatio * 10000.0) / 100.0;
        boolean safe = usedPercent < alertThreshold;
        String desc = safe
                ? String.format("磁盘空间充足，当前已用 %.2f%% (阈值 %.0f%%)", usedPercent, alertThreshold)
                : String.format("警告：磁盘使用率达到 %.2f%%，已触发高水位熔断保护 (阈值 %.0f%%)", usedPercent, alertThreshold);
        return new DiskHealthStatus(totalSpace, usableSpace, used, usedPercent, safe, alertThreshold, desc);
    }
}
