package com.affiliate.platform.dsp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 需求方动态出价倍率调整引擎 (DSP Bid Modifier Engine)
 * <p>
 * 商业级 DSP（The Trade Desk / DV360）的核心出价调控器：
 * 在 Campaign 基准底价（Base Bid）上应用多维乘法系数矩阵，实现精准 ROI 最大化：
 * 1. 终端形态倍率 (Device Multiplier)：iOS, Android, CTV, Desktop;
 * 2. 国家地域倍率 (Geo Tier Multiplier)：Tier-1 高价值国家溢价，长尾流量折价;
 * 3. 分时段倍率 (Dayparting Hour Multiplier)：早晚高峰溢价，深夜抑制;
 * 4. 媒体位质量评分倍率 (Placement Quality Multiplier)：优质媒体加权竞胜;
 * 5. 几何对数阻尼与复合倍率钳位保护 (Log-Damped Aggregation & Clamping)：抑制多维连乘组合爆炸，避免出价失控。
 */
@Service
public class BidModifierEngine {

    // 默认阻尼系数 (0.50 ~ 0.85，0.70 为工业界标准折中，1.0 为纯线性乘积)
    private static final double DEFAULT_DAMPING_FACTOR = 0.70;

    // 复合倍率下限与上限全局熔断护栏
    private static final double MIN_COMPOSITE_MULTIPLIER = 0.30;
    private static final double MAX_COMPOSITE_MULTIPLIER = 2.50;

    // 默认设备形态倍率
    private final Map<Integer, BigDecimal> deviceMultipliers = new ConcurrentHashMap<>(Map.of(
            1, new BigDecimal("1.00"), // Mobile Android
            2, new BigDecimal("1.35"), // Mobile iOS (更高 ARPU)
            3, new BigDecimal("1.10"), // Desktop Web
            4, new BigDecimal("1.75")  // Connected TV (OTT 高溢价)
    ));

    // 默认国家梯队倍率
    private final Map<String, BigDecimal> geoMultipliers = new ConcurrentHashMap<>(Map.of(
            "US", new BigDecimal("1.50"),
            "GB", new BigDecimal("1.40"),
            "DE", new BigDecimal("1.30"),
            "CA", new BigDecimal("1.35"),
            "JP", new BigDecimal("1.45"),
            "AU", new BigDecimal("1.30")
    ));

    // 分时段倍率 (0~23 小时)
    private final Map<Integer, BigDecimal> hourMultipliers = new ConcurrentHashMap<>();

    private volatile double dampingFactor = DEFAULT_DAMPING_FACTOR;
    private volatile boolean dampingEnabled = false;

    public BidModifierEngine() {
        // 初始化 24 小时默认起伏曲线：早晨 02:00-06:00 抑价 0.7x，晚间高峰 19:00-22:00 溢价 1.25x
        for (int h = 0; h < 24; h++) {
            if (h >= 2 && h <= 6) {
                hourMultipliers.put(h, new BigDecimal("0.70"));
            } else if (h >= 19 && h <= 22) {
                hourMultipliers.put(h, new BigDecimal("1.25"));
            } else {
                hourMultipliers.put(h, new BigDecimal("1.00"));
            }
        }
    }

    /**
     * 计算多维调优后的实际出价（兼容默认模式）
     *
     * @param baseBid          广告组原始基准 CPM 出价
     * @param deviceType       设备形态
     * @param country          国家代码 (ISO-2)
     * @param hourOfDay        当前所处小时 (0~23)
     * @param placementQuality 媒体位历史转化质量分 (0.50 ~ 1.50)
     * @param maxBidLimit      单次出价最高保护上限 (防止误出天价)
     * @return 经多维矩阵调优后的最终竞价出价 (Final Bid CPM)
     */
    public BigDecimal calculateAdjustedBid(
            BigDecimal baseBid,
            int deviceType,
            String country,
            int hourOfDay,
            BigDecimal placementQuality,
            BigDecimal maxBidLimit
    ) {
        if (dampingEnabled) {
            return calculateAdjustedBidDamped(
                    baseBid, deviceType, country, hourOfDay, placementQuality, maxBidLimit,
                    dampingFactor, MIN_COMPOSITE_MULTIPLIER, MAX_COMPOSITE_MULTIPLIER
            );
        } else {
            return calculateAdjustedBidLinear(
                    baseBid, deviceType, country, hourOfDay, placementQuality, maxBidLimit
            );
        }
    }

    /**
     * 工业级对数几何阻尼调价算法 (Log-Damped Geometric Bidding)
     * <p>
     * 公式：
     * Composite = clamp(Product(M_i)^damping, minMultiplier, maxMultiplier)
     * FinalBid = min(BaseBid * Composite, maxBidLimit)
     */
    public BigDecimal calculateAdjustedBidDamped(
            BigDecimal baseBid,
            int deviceType,
            String country,
            int hourOfDay,
            BigDecimal placementQuality,
            BigDecimal maxBidLimit,
            double damping,
            double minMultiplier,
            double maxMultiplier
    ) {
        if (baseBid == null || baseBid.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        double devMult = deviceMultipliers.getOrDefault(deviceType, BigDecimal.ONE).doubleValue();
        double geoMult = geoMultipliers.getOrDefault(country != null ? country.toUpperCase() : "", BigDecimal.ONE).doubleValue();
        double hourMult = hourMultipliers.getOrDefault(hourOfDay % 24, BigDecimal.ONE).doubleValue();
        double qualityMult = placementQuality != null && placementQuality.signum() > 0 ? placementQuality.doubleValue() : 1.0;

        // 原始连乘倍率
        double rawComposite = devMult * geoMult * hourMult * qualityMult;

        // 几何加权对数阻尼：Composite = exp(damping * ln(rawComposite)) = rawComposite ^ damping
        double dampedComposite;
        if (damping > 0.0 && rawComposite > 0.0) {
            dampedComposite = Math.pow(rawComposite, damping);
        } else {
            dampedComposite = rawComposite;
        }

        // 全局安全范围钳位 (Clamp)
        double clampedComposite = Math.max(minMultiplier, Math.min(maxMultiplier, dampedComposite));

        // 计算最终出价
        BigDecimal adjusted = baseBid.multiply(BigDecimal.valueOf(clampedComposite))
                .setScale(4, RoundingMode.HALF_UP);

        // 熔断上限保护
        if (maxBidLimit != null && maxBidLimit.signum() > 0 && adjusted.compareTo(maxBidLimit) > 0) {
            return maxBidLimit;
        }

        return adjusted;
    }

    /**
     * 无阻尼纯线性连乘出价
     */
    public BigDecimal calculateAdjustedBidLinear(
            BigDecimal baseBid,
            int deviceType,
            String country,
            int hourOfDay,
            BigDecimal placementQuality,
            BigDecimal maxBidLimit
    ) {
        if (baseBid == null || baseBid.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal devMult = deviceMultipliers.getOrDefault(deviceType, BigDecimal.ONE);
        BigDecimal geoMult = geoMultipliers.getOrDefault(country != null ? country.toUpperCase() : "", BigDecimal.ONE);
        BigDecimal hourMult = hourMultipliers.getOrDefault(hourOfDay % 24, BigDecimal.ONE);
        BigDecimal qualityMult = placementQuality != null && placementQuality.signum() > 0 ? placementQuality : BigDecimal.ONE;

        BigDecimal adjusted = baseBid
                .multiply(devMult)
                .multiply(geoMult)
                .multiply(hourMult)
                .multiply(qualityMult)
                .setScale(4, RoundingMode.HALF_UP);

        if (maxBidLimit != null && maxBidLimit.signum() > 0 && adjusted.compareTo(maxBidLimit) > 0) {
            return maxBidLimit;
        }

        return adjusted;
    }

    public void setDampingFactor(double dampingFactor) {
        this.dampingFactor = dampingFactor;
    }

    public double getDampingFactor() {
        return dampingFactor;
    }

    public void setDampingEnabled(boolean dampingEnabled) {
        this.dampingEnabled = dampingEnabled;
    }

    public boolean isDampingEnabled() {
        return dampingEnabled;
    }

    public void setDeviceMultiplier(int deviceType, BigDecimal multiplier) {
        deviceMultipliers.put(deviceType, multiplier);
    }

    public void setGeoMultiplier(String country, BigDecimal multiplier) {
        geoMultipliers.put(country.toUpperCase(), multiplier);
    }

    public void setHourMultiplier(int hour, BigDecimal multiplier) {
        hourMultipliers.put(hour % 24, multiplier);
    }
}
