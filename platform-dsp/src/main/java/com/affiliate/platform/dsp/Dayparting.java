package com.affiliate.platform.dsp;

import java.time.DayOfWeek;
import java.util.*;

/**
 * 广告投放分时段排期矩阵 (Dayparting Schedule Matrix)
 * <p>
 * 商业广告投放中核心的时段定向能力：
 * 采用 7 天（周一至周日） x 24 小时（0-23时）位图/集合矩阵表示，
 * 支持按小时级别精准控制竞价预算开放与冻结。
 */
public record Dayparting(Map<DayOfWeek, Set<Integer>> schedule) {

    public Dayparting {
        // 防御性拷贝，保证不可变性
        Map<DayOfWeek, Set<Integer>> map = new EnumMap<>(DayOfWeek.class);
        if (schedule != null) {
            schedule.forEach((day, hours) -> {
                if (hours != null) {
                    map.put(day, Set.copyOf(hours));
                }
            });
        }
        schedule = Collections.unmodifiableMap(map);
    }

    /**
     * 判断指定的星期与时刻是否处于允许竞价的投放窗口内
     *
     * @param dayOfWeek 星期几 (MONDAY..SUNDAY)
     * @param hourOfDay 小时 (0..23)
     * @return true 代表允许投放出价，false 代表时段过滤暂停
     */
    public boolean allows(DayOfWeek dayOfWeek, int hourOfDay) {
        if (schedule.isEmpty()) {
            return true; // 空配置默认 7x24 全时段投放
        }
        Set<Integer> activeHours = schedule.get(dayOfWeek);
        return activeHours != null && activeHours.contains(hourOfDay);
    }

    /**
     * 工厂方法：全天候 7x24 小时全开
     */
    public static Dayparting allHours() {
        return new Dayparting(Map.of());
    }

    /**
     * 工厂方法：典型工作时间投放 (周一至周五 09:00 ~ 18:00)
     */
    public static Dayparting businessHours() {
        Map<DayOfWeek, Set<Integer>> map = new EnumMap<>(DayOfWeek.class);
        Set<Integer> workHours = new HashSet<>();
        for (int h = 9; h <= 18; h++) {
            workHours.add(h);
        }
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            map.put(day, workHours);
        }
        return new Dayparting(map);
    }
}
