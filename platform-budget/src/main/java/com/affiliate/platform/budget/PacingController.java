package com.affiliate.platform.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工业级广告预算平滑消耗控速引擎 (Budget Pacing Controller)
 * <p>
 * 解决 RTB 竞价中经典“预算早消耗完毕 (Premature Budget Exhaustion)”问题：
 * 1. UNIFORM（均匀平滑）：将 24 小时划分为等额预算片，动态阻断超前消耗；
 * 2. ASAP（尽快投放）：不进行控速节流，适合冲量抢量的短跑活动；
 * 3. TRAFFIC_AWARE（流量感知）：根据自然互联网日流量曲线（晚高峰高权重、深夜低权重）自适应分配出价概率。
 */
public class PacingController {

    // 默认 24 小时互联网基准流量权重分布（归一化系数，总和为 1.0）
    private static final double[] DEFAULT_HOURLY_TRAFFIC_CURVE = {
            0.015, 0.010, 0.008, 0.007, 0.008, 0.012, // 00:00 - 05:00 (深夜低谷)
            0.025, 0.040, 0.055, 0.060, 0.058, 0.055, // 06:00 - 11:00 (早间攀升)
            0.052, 0.050, 0.048, 0.050, 0.055, 0.062, // 12:00 - 17:00 (午后平稳)
            0.075, 0.085, 0.090, 0.080, 0.055, 0.035  // 18:00 - 23:00 (黄金晚高峰)
    };

    public enum PacingMode {
        /** 24小时等额均匀平滑 */
        UNIFORM,
        /** 尽快投放无节流 */
        ASAP,
        /** 流量感知自适应平滑 */
        TRAFFIC_AWARE
    }

    private final PacingMode mode;
    private final double[] trafficCurve;

    public PacingController(PacingMode mode, double[] customTrafficCurve) {
        this.mode = mode == null ? PacingMode.TRAFFIC_AWARE : mode;
        if (customTrafficCurve != null && customTrafficCurve.length == 24) {
            this.trafficCurve = Arrays.copyOf(customTrafficCurve, 24);
        } else {
            this.trafficCurve = DEFAULT_HOURLY_TRAFFIC_CURVE;
        }
    }

    public PacingController(PacingMode mode) {
        this(mode, null);
    }

    public PacingController() {
        this(PacingMode.TRAFFIC_AWARE, null);
    }

    /**
     * 判断当前时刻是否应当允许执行竞价出价 (Pacing Decision)
     *
     * @param hourOfDay     当前小时 (0..23)
     * @param currentSpend  当日当前累计消耗金额
     * @param dailyBudget   当日设定预算限额
     * @return true 代表允许竞价，false 代表因消耗过快被控速采样抛弃
     */
    public boolean shouldBid(int hourOfDay, BigDecimal currentSpend, BigDecimal dailyBudget) {
        if (dailyBudget == null || dailyBudget.signum() <= 0) {
            return false;
        }
        if (currentSpend == null || currentSpend.signum() < 0) {
            currentSpend = BigDecimal.ZERO;
        }

        // 1. 若当日消耗已达到或超过每日限额，100% 阻断
        if (currentSpend.compareTo(dailyBudget) >= 0) {
            return false;
        }

        // 2. ASAP 模式不作平滑控速
        if (mode == PacingMode.ASAP) {
            return true;
        }

        // 3. 计算截至当前小时结束期望的累计消耗占比 (Expected Cumulative Ratio)
        int h = Math.max(0, Math.min(23, hourOfDay));
        double expectedRatio;

        if (mode == PacingMode.UNIFORM) {
            // (h + 1) / 24.0
            expectedRatio = (h + 1) / 24.0;
        } else {
            // 累计流量曲线权重
            double sum = 0.0;
            for (int i = 0; i <= h; i++) {
                sum += trafficCurve[i];
            }
            expectedRatio = Math.min(1.0, sum);
        }

        BigDecimal expectedSpend = dailyBudget.multiply(BigDecimal.valueOf(expectedRatio));

        // 4. 若当前消耗尚未达到期望进度，100% 允许出价放行
        if (currentSpend.compareTo(expectedSpend) <= 0) {
            return true;
        }

        // 5. 若已超前消耗，计算阻尼出价通过率 (Pass Probability)
        // 超前程度越大，抛币通过率越低
        double overspendRatio = currentSpend.subtract(expectedSpend)
                .divide(dailyBudget, 4, RoundingMode.HALF_UP)
                .doubleValue();

        // 阻尼因子：每超前 5% 预算，通过率下降 20%
        double passProbability = Math.max(0.05, 1.0 - (overspendRatio * 4.0));

        return ThreadLocalRandom.current().nextDouble() < passProbability;
    }

    public PacingMode getMode() {
        return mode;
    }
}
