package com.affiliate.platform.dsp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 实时竞价流量环境上下文 (Traffic Context)
 * <p>
 * 封装进入 DSP 撮合与定向匹配的所有维度信息（地理、设备、时段、域名、用户标签）。
 */
public record TrafficContext(
        String domain,
        int deviceType,
        LocalDate date,
        int hourOfDay,
        DayOfWeek dayOfWeek,
        String country,
        Set<String> userSegments
) {
    public TrafficContext {
        domain = domain == null ? "" : domain.toLowerCase();
        country = country == null ? "" : country.toUpperCase();
        userSegments = userSegments == null ? Set.of() : Set.copyOf(userSegments);
    }

    /**
     * 快捷构造器：用于基础 3 维匹配
     */
    public static TrafficContext of(String domain, int deviceType, LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        return new TrafficContext(domain, deviceType, d, 12, d.getDayOfWeek(), null, Set.of());
    }
}
