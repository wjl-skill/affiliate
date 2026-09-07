package com.affiliate.platform.affiliate.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网盟推广计划领域实体 (Affiliate Offer Domain Record)
 * <p>
 * 联盟营销平台（Affise / Everflow / Tune）的核心业务载体：
 * 1. 承载广告主创建的转化活动（CPA、CPL、CPS、CPI、CPC、RevShare）；
 * 2. 具备日转化单量配额（Daily Conversion Cap）与日资金上限（Daily Revenue Cap）；
 * 3. 支持超限自动重定向到保底兜底计划（Fallback Offer ID）；
 * 4. 支持国家地理与终端设备类型过滤。
 */
public record Offer(
        String id,
        String tenantId,
        @NotBlank String advertiserId,
        @NotBlank String title,
        @NotBlank String landingPageUrl,
        @NotNull PayoutType payoutType,
        @DecimalMin("0.00") BigDecimal defaultPayout,
        @DecimalMin("0.00") BigDecimal defaultRevenue,
        Status status,
        int dailyConversionCap,
        BigDecimal dailyRevenueCap,
        String fallbackOfferId,
        Set<String> allowedCountries,
        Set<Integer> allowedDevices,
        Instant expiresAt,
        Instant createdAt
) {
    public Offer {
        status = status == null ? Status.ACTIVE : status;
        allowedCountries = allowedCountries == null ? Set.of() : allowedCountries.stream().map(String::toUpperCase).collect(Collectors.toUnmodifiableSet());
        allowedDevices = allowedDevices == null ? Set.of() : Set.copyOf(allowedDevices);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum PayoutType {
        /** 按单次转化行为付费 (Cost Per Action) */
        CPA,
        /** 按注册线索付费 (Cost Per Lead) */
        CPL,
        /** 按订单销售额分成 (Cost Per Sale / RevShare) */
        CPS,
        /** 按应用安装付费 (Cost Per Install) */
        CPI,
        /** 按单次点击付费 (Cost Per Click) */
        CPC
    }

    public enum Status {
        /** 正常投放接收转化 */
        ACTIVE,
        /** 暂停：不接收新点击 */
        PAUSED,
        /** 已过期 */
        EXPIRED
    }

    /**
     * 判断当前 Offer 是否仍处于可用状态
     */
    public boolean isAvailable() {
        if (status != Status.ACTIVE) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    /**
     * 校验流量环境（国家、设备类型）是否符合投放限制
     */
    public boolean matches(String country, int deviceType) {
        if (!isAvailable()) {
            return false;
        }
        if (!allowedCountries.isEmpty() && (country == null || !allowedCountries.contains(country.toUpperCase()))) {
            return false;
        }
        if (!allowedDevices.isEmpty() && !allowedDevices.contains(deviceType)) {
            return false;
        }
        return true;
    }
}
