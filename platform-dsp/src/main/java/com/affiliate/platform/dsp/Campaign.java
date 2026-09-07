package com.affiliate.platform.dsp;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 需求方广告活动领域实体 (DSP Campaign Domain Record)
 * <p>
 * 管理广告主的投放排期、日预算限制、出价上限及域名/设备定向条件。
 *
 * @param id                活动全局唯一主键 ID
 * @param advertiserId      所属广告主客户唯一标识
 * @param name              活动展示名称（例如 "2026 夏季促销大促"）
 * @param startDate         投放排期开始日期（包含）
 * @param endDate           投放排期结束日期（包含）
 * @param dailyBudget       每日资金预算限额（不可为负数）
 * @param maxBid            单次出价最高保护上限（CPM 单位）
 * @param targetDomains     定向投放的目标域名白名单（空集合代表不限域名）
 * @param targetDeviceTypes 定向设备类型编码集合（如 1-手机, 2-平板, 4-桌面PC；空集合代表全设备投放）
 * @param status            活动状态（DRAFT 草稿, ACTIVE 投放中, PAUSED 暂停, ENDED 已结束）
 * @param createdAt         活动创建时间戳
 */
public record Campaign(
        String id,
        @NotBlank String advertiserId,
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @DecimalMin("0.00") BigDecimal dailyBudget,
        @DecimalMin("0.00") BigDecimal maxBid,
        Set<String> targetDomains,
        Set<Integer> targetDeviceTypes,
        Status status,
        Instant createdAt
) {
    /**
     * 紧凑构造器 - 校验投放排期时间线与定向条件归一化
     */
    public Campaign {
        // 结束日期严禁早于开始日期
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        // 目标域名全小写归一化
        targetDomains = targetDomains == null
                ? Set.of()
                : targetDomains.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        // 设备类型防御性拷贝
        targetDeviceTypes = targetDeviceTypes == null ? Set.of() : Set.copyOf(targetDeviceTypes);
    }

    /**
     * 广告活动生命周期状态
     */
    public enum Status {
        /** 草稿：尚未启动 */
        DRAFT,
        /** 投放中：正常参与实时竞价撮合 */
        ACTIVE,
        /** 暂停：人工或预算不足暂停 */
        PAUSED,
        /** 已结束：到达投放排期终止日 */
        ENDED
    }

    /**
     * 切换活动的投放状态并返回新的不可变实体
     *
     * @param active true 为 ACTIVE，false 为 PAUSED
     * @return 状态变更后的 Campaign 实例
     */
    public Campaign activate(boolean active) {
        return new Campaign(id, advertiserId, name, startDate, endDate, dailyBudget, maxBid,
                targetDomains, targetDeviceTypes, active ? Status.ACTIVE : Status.PAUSED, createdAt);
    }

    /**
     * 判定当前广告活动是否匹配指定的流量上下文 (Targeting Match Engine)
     *
     * @param domain     媒体请求来源网站域名
     * @param deviceType 客户端设备类型编号
     * @param date       当前竞价日期
     * @return true 代表所有定向条件均吻合且状态处于 ACTIVE
     */
    public boolean matches(String domain, int deviceType, LocalDate date) {
        return matches(TrafficContext.of(domain, deviceType, date));
    }

    /**
     * 全维度流量上下文定向匹配 (Full Context Targeting Match)
     *
     * @param ctx 综合流量环境上下文（包含时段、地域、设备、域名与人群标签）
     * @return true 代表通过活动级定向过滤
     */
    public boolean matches(TrafficContext ctx) {
        if (status != Status.ACTIVE) {
            return false;
        }
        // 1. 排期日期匹配：当前日期介于 startDate 与 endDate 之间
        boolean dateMatch = ctx.date() == null || (!ctx.date().isBefore(startDate) && !ctx.date().isAfter(endDate));
        // 2. 域名定向匹配：目标域名为空（不限）或包含当前媒体域名
        boolean domainMatch = targetDomains.isEmpty() || (!ctx.domain().isBlank() && targetDomains.contains(ctx.domain()));
        // 3. 设备定向匹配：目标设备为空（不限）或包含当前请求设备类型
        boolean deviceMatch = targetDeviceTypes.isEmpty() || targetDeviceTypes.contains(ctx.deviceType());

        return dateMatch && domainMatch && deviceMatch;
    }
}
