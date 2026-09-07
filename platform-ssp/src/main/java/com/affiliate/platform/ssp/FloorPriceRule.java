package com.affiliate.platform.ssp;

import java.math.BigDecimal;

/**
 * 媒体多维动态底价规则实体 (Dynamic Floor Price Rule)
 * <p>
 * 支持根据国家地域、设备类型与请求小时时段动态计算版位的软硬底价：
 * 1. Hard Floor（硬底价）：出价必须严格高于此价格，否则判定流拍；
 * 2. Soft Floor（软底价）：高于此价格直接进入第一价竞价，介于软硬底价之间走次高价加价。
 */
public record FloorPriceRule(
        String id,
        String slotId,
        String country,
        Integer deviceType,
        Integer startHour,
        Integer endHour,
        BigDecimal hardFloor,
        BigDecimal softFloor
) {
    public FloorPriceRule {
        country = (country == null || country.isBlank()) ? null : country.toUpperCase();
        if (hardFloor == null || hardFloor.signum() < 0) {
            hardFloor = BigDecimal.ZERO;
        }
        if (softFloor == null || softFloor.compareTo(hardFloor) < 0) {
            softFloor = hardFloor;
        }
    }

    /**
     * 判断当前流量上下文是否匹配该底价规则
     */
    public boolean matches(String reqCountry, int reqDeviceType, int reqHour) {
        if (country != null && !country.equalsIgnoreCase(reqCountry)) {
            return false;
        }
        if (deviceType != null && deviceType != reqDeviceType) {
            return false;
        }
        if (startHour != null && endHour != null) {
            if (reqHour < startHour || reqHour > endHour) {
                return false;
            }
        }
        return true;
    }
}
