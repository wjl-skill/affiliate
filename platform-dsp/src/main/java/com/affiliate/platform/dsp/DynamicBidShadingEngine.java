package com.affiliate.platform.dsp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 需求方第一价格拍卖动态出价微调引擎 (DSP Dynamic Bid Shading Engine)
 * <p>
 * 商业级 DSP（The Trade Desk / DV360 / Xandr）必备的核心出价算法组件：
 * 1. 在第一价格拍卖（First-Price Auction）机制下，解决“胜者诅咒 (Winner's Curse)”与资金过度消耗；
 * 2. 建立连续对数几率曲线 (Logistic Win-Rate Curve) 模拟胜率：P(Win | b) = 1 / (1 + exp(-k * (b - b0)))；
 * 3. 求解期望剩余价值最大化目标函数：max_b (V - b) * P(Win | b)，输出最优打折出价；
 * 4. 具备在线闭环自适应学习机制，根据实际胜出与落败反馈动态更新市场价格水位 b0 与竞争烈度 k；
 * 5. 配备底价护栏 (Floor Guard) 与合理折扣安全区间 [0.50, 0.98]。
 */
@Service
public class DynamicBidShadingEngine {

    // 默认探索折扣比率
    private static final double DEFAULT_SHADING_RATIO = 0.85;

    // 折扣保护下界与上界
    private static final double MIN_SHADING_RATIO = 0.50;
    private static final double MAX_SHADING_RATIO = 0.98;

    // 市场维度画像容器：Key 为 "slotId:geo:device"
    private final ConcurrentMap<String, MarketPricingProfile> profileMap = new ConcurrentHashMap<>();

    /**
     * 计算最优出价与降价系数 (Dynamic Bid Shading)
     *
     * @param valuation      广告主评估的真值/上限 (eCPM USD)
     * @param floorPrice     媒体底价 (Reserve Floor)
     * @param marketSegment  市场画像分段维度（如 "slot123:US:mobile"）
     * @return 最优出价决策结果
     */
    public ShadingResult calculateShadedBid(
            BigDecimal valuation,
            BigDecimal floorPrice,
            String marketSegment
    ) {
        if (valuation == null || valuation.signum() <= 0) {
            return new ShadingResult(BigDecimal.ZERO, 1.0, 0.0);
        }

        double v = valuation.doubleValue();
        double floor = floorPrice != null ? Math.max(0.0, floorPrice.doubleValue()) : 0.0;

        // 若真值本身已低于底价，无法获胜
        if (v < floor) {
            return new ShadingResult(BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP), 1.0, 0.0);
        }

        MarketPricingProfile profile = marketSegment != null
                ? profileMap.get(marketSegment)
                : null;

        // 若无历史统计画像，使用平滑默认折扣 0.85，且不低于底价
        if (profile == null || profile.sampleCount < 10) {
            double defaultBid = Math.max(floor, v * DEFAULT_SHADING_RATIO);
            defaultBid = Math.min(v, defaultBid);
            double ratio = v > 0 ? defaultBid / v : 1.0;
            return new ShadingResult(
                    BigDecimal.valueOf(defaultBid).setScale(4, RoundingMode.HALF_UP),
                    ratio,
                    estimateWinRate(defaultBid, 0.6 * v, 1.5)
            );
        }

        // 基于 Logistic 胜率曲线求解最优出价
        // 目标函数: f(b) = (v - b) * (1 / (1 + exp(-k * (b - b0))))
        double b0 = profile.marketMidPrice;
        double k = profile.marketSteepness;

        double bestBid = v * DEFAULT_SHADING_RATIO;
        double maxSurplus = -1.0;
        double bestWinRate = 0.5;

        // 在 [max(floor, v * MIN_SHADING_RATIO), min(v, v * MAX_SHADING_RATIO)] 区间内执行高精度快速黄金分割/步进探测
        double low = Math.max(floor, v * MIN_SHADING_RATIO);
        double high = Math.min(v, Math.max(low, v * MAX_SHADING_RATIO));

        int steps = 20;
        double stepSize = (high - low) / steps;

        for (int i = 0; i <= steps; i++) {
            double b = low + i * stepSize;
            double winProb = estimateWinRate(b, b0, k);
            double surplus = (v - b) * winProb;
            if (surplus > maxSurplus) {
                maxSurplus = surplus;
                bestBid = b;
                bestWinRate = winProb;
            }
        }

        // 最终安全保障
        bestBid = Math.max(floor, Math.min(v, bestBid));
        double finalRatio = v > 0 ? bestBid / v : 1.0;

        return new ShadingResult(
                BigDecimal.valueOf(bestBid).setScale(4, RoundingMode.HALF_UP),
                finalRatio,
                bestWinRate
        );
    }

    /**
     * 竞价结果在线闭环反馈：记录竞得事件，自适应微调市场出价水位
     */
    public void recordWin(String marketSegment, double winningBid, double secondBid) {
        if (marketSegment == null) return;
        MarketPricingProfile profile = profileMap.computeIfAbsent(marketSegment, k -> new MarketPricingProfile());
        profile.updateOnWin(winningBid, secondBid);
    }

    /**
     * 竞价结果在线闭环反馈：记录竞价失败事件
     */
    public void recordLoss(String marketSegment, double ourBid) {
        if (marketSegment == null) return;
        MarketPricingProfile profile = profileMap.computeIfAbsent(marketSegment, k -> new MarketPricingProfile());
        profile.updateOnLoss(ourBid);
    }

    public void clearProfiles() {
        profileMap.clear();
    }

    /**
     * 胜率估算曲线 (Logistic Curve)
     */
    public static double estimateWinRate(double bid, double b0, double k) {
        double exponent = -k * (bid - b0);
        if (exponent > 50.0) return 0.0;
        if (exponent < -50.0) return 1.0;
        return 1.0 / (1.0 + Math.exp(exponent));
    }

    public record ShadingResult(
            BigDecimal shadedBid,
            double shadingRatio,
            double predictedWinRate
    ) {}

    /**
     * 市场段竞价水位与敏感度画像
     */
    public static class MarketPricingProfile {
        public volatile double marketMidPrice = 2.0;  // 市场竞争水位 b0
        public volatile double marketSteepness = 1.2; // 市场敏感度 k
        public volatile long sampleCount = 0;

        public synchronized void updateOnWin(double winningBid, double secondBid) {
            sampleCount++;
            // 采用指数平滑移动平均 (EMA) 更新市场出清中位价
            double clearingRef = secondBid > 0 ? secondBid : winningBid * 0.9;
            double alpha = Math.max(0.05, 1.0 / Math.min(sampleCount, 100));
            marketMidPrice = (1 - alpha) * marketMidPrice + alpha * clearingRef;
        }

        public synchronized void updateOnLoss(double ourBid) {
            sampleCount++;
            // 落败意味着出价偏低，平滑上调市场水位以提高后续中标概率
            double alpha = Math.max(0.05, 1.0 / Math.min(sampleCount, 100));
            marketMidPrice = (1 - alpha) * marketMidPrice + alpha * (ourBid * 1.15);
        }
    }
}
