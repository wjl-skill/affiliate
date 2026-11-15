package com.affiliate.platform.dsp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 生产级日内预算平滑消耗控制器 (PID Intraday Budget Pacing Controller)
 * <p>
 * 解决商业 DSP（The Trade Desk / DV360）日内预算分配不均导致的“早间光速撞线停投”与“晚间黄金流量错过”问题：
 * 1. 基于比例-积分-微分 (PID) 闭环控制器实时追踪「目标消耗进度 r(t)」与「实际消耗进度 y(t)」的偏差 e(t) = r(t) - y(t)；
 * 2. 动态产出两类调控信号：
 *    - 出价调节乘数 (Bid Pacing Multiplier ∈ [0.20, 1.80])：消耗超前时自动压低出价攫取高性价比流量，消耗滞后时适度提价追赶进度；
 *    - 概率性参竞放行率 (Probabilistic Throttling Rate ∈ [0.05, 1.00])：严重超支风险时按概率丢弃低优流量，实现平滑匀速投放。
 */
@Service
public class BudgetPacingController {

    private static final double DEFAULT_KP = 1.20;
    private static final double DEFAULT_KI = 0.35;
    private static final double DEFAULT_KD = 0.15;

    private static final double MIN_BID_MULTIPLIER = 0.20;
    private static final double MAX_BID_MULTIPLIER = 1.80;

    private static final double MIN_PASS_PROBABILITY = 0.05;
    private static final double MAX_PASS_PROBABILITY = 1.00;

    // 24 小时标准商业流量权重分布（模拟全天流量潮汐曲线，总和归一化为 1.0）
    private static final double[] HOURLY_TRAFFIC_WEIGHTS = {
            0.020, 0.015, 0.012, 0.010, 0.012, 0.018, // 00:00 - 05:59 低谷
            0.028, 0.038, 0.048, 0.052, 0.055, 0.056, // 06:00 - 11:59 早高峰爬坡
            0.054, 0.052, 0.054, 0.055, 0.056, 0.058, // 12:00 - 17:59 日间平稳
            0.062, 0.068, 0.072, 0.058, 0.032, 0.015  // 18:00 - 23:59 晚间黄金高峰
    };

    private final ConcurrentMap<String, PacingState> campaignStates = new ConcurrentHashMap<>();

    /**
     * 计算指定 Campaign 在当前日内时刻的 PID 匀速投放控制决策
     *
     * @param campaignId        广告活动标识
     * @param dailyBudget       当日总预算
     * @param cumulativeSpent   当日已累计消耗金额
     * @param elapsedDayFraction 当日已流逝时间比例 (0.0 ~ 1.0，例如正午 12:00 为 0.50)
     * @param trafficCurveMode  是否启用按黄金时段流量潮汐加权曲线（false 则为匀速直线分配）
     * @return PacingDecision 控制信号
     */
    public PacingDecision evaluatePacing(
            String campaignId,
            BigDecimal dailyBudget,
            BigDecimal cumulativeSpent,
            double elapsedDayFraction,
            boolean trafficCurveMode
    ) {
        if (dailyBudget == null || dailyBudget.signum() <= 0) {
            return new PacingDecision(campaignId, 0.0, 0.0, 0.0, BigDecimal.ZERO, 0.0, true);
        }

        double clampedTime = Math.max(0.0, Math.min(1.0, elapsedDayFraction));
        double spentRatio = cumulativeSpent != null && cumulativeSpent.signum() > 0
                ? cumulativeSpent.doubleValue() / dailyBudget.doubleValue()
                : 0.0;

        if (spentRatio >= 1.0) {
            return new PacingDecision(campaignId, clampedTime, spentRatio, -1.0, BigDecimal.ZERO, 0.0, true);
        }

        double targetRatio = trafficCurveMode ? computeTrafficWeightedTarget(clampedTime) : clampedTime;
        // 误差 e(t) = 目标消耗比例 - 实际消耗比例（< 0 表示消耗过快超支，> 0 表示消耗滞后欠量）
        double error = targetRatio - spentRatio;

        PacingState state = campaignStates.computeIfAbsent(
                campaignId != null ? campaignId : "default", k -> new PacingState()
        );

        double pidAdjustment = state.stepPid(error);

        // 1. 计算出价调优乘数：基准为 1.0 + pidAdjustment，钳位在 [0.20, 1.80]
        double rawMultiplier = 1.0 + pidAdjustment;
        double clampedMultiplier = Math.max(MIN_BID_MULTIPLIER, Math.min(MAX_BID_MULTIPLIER, rawMultiplier));
        BigDecimal bidMultiplier = BigDecimal.valueOf(clampedMultiplier).setScale(4, RoundingMode.HALF_UP);

        // 2. 计算概率性节流放行率：当消耗不超前 (error >= 0) 时 100% 放行；消耗超前时随超支幅度平滑下调放行率
        double passProbability = MAX_PASS_PROBABILITY;
        if (error < -0.02) {
            double overspendSeverity = Math.abs(error);
            passProbability = Math.max(MIN_PASS_PROBABILITY, Math.exp(-3.0 * overspendSeverity));
        }

        return new PacingDecision(
                campaignId,
                Math.round(targetRatio * 10000.0) / 10000.0,
                Math.round(spentRatio * 10000.0) / 10000.0,
                Math.round(error * 10000.0) / 10000.0,
                bidMultiplier,
                Math.round(passProbability * 10000.0) / 10000.0,
                false
        );
    }

    private double computeTrafficWeightedTarget(double elapsedDayFraction) {
        double exactHour = elapsedDayFraction * 24.0;
        int fullHours = Math.min(24, (int) Math.floor(exactHour));
        double partialHour = exactHour - fullHours;

        double cumulative = 0.0;
        for (int h = 0; h < fullHours; h++) {
            cumulative += HOURLY_TRAFFIC_WEIGHTS[h];
        }
        if (fullHours < 24 && partialHour > 0.0) {
            cumulative += HOURLY_TRAFFIC_WEIGHTS[fullHours] * partialHour;
        }
        return Math.max(0.0, Math.min(1.0, cumulative));
    }

    public void resetCampaignState(String campaignId) {
        if (campaignId != null) {
            campaignStates.remove(campaignId);
        }
    }

    private static final class PacingState {
        private double integralError = 0.0;
        private double lastError = 0.0;
        private boolean initialized = false;

        synchronized double stepPid(double currentError) {
            // 积分抗饱和钳位 (Anti-Windup Clamping)
            integralError = Math.max(-0.60, Math.min(0.60, integralError * 0.85 + currentError));
            double derivative = initialized ? (currentError - lastError) : 0.0;
            lastError = currentError;
            initialized = true;
            return DEFAULT_KP * currentError + DEFAULT_KI * integralError + DEFAULT_KD * derivative;
        }
    }

    public record PacingDecision(
            String campaignId,
            double targetSpendRatio,
            double actualSpendRatio,
            double pacingError,
            BigDecimal bidPacingMultiplier,
            double passProbability,
            boolean budgetExhausted
    ) {}
}
