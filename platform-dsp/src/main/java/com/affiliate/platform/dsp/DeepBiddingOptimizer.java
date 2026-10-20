package com.affiliate.platform.dsp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 生产级需求方深度智能出价优化器 (Deep Bidding Optimizer)
 * <p>
 * 整合 Deep Factorization Machine 预测引擎与 Logistic Bid Shading 微调引擎：
 * 1. 深度推断当前流量环境下的综合点击率 pCTR 与转化率 pCVR；
 * 2. 依据广告主目标成本 (Target CPA) 计算每次曝光的期望真实价值：
 *    Valuation (eCPM) = Target_CPA * pCVR * pCTR * 1000；
 * 3. 驱动 DynamicBidShadingEngine 执行第一价格拍卖降价调优；
 * 4. 强约束底价护栏 (Floor Guard) 与最高出价钳位 (Max Bid Clamp)，杜绝异常高价与流拍。
 */
@Service
public class DeepBiddingOptimizer {

    private static final Logger log = LoggerFactory.getLogger(DeepBiddingOptimizer.class);

    private final DeepCtrCvrInferenceEngine inferenceEngine;
    private final DynamicBidShadingEngine shadingEngine;

    // 默认最高安全出价 (eCPM USD)
    public static final BigDecimal DEFAULT_MAX_BID_GUARD = BigDecimal.valueOf(50.00);
    // 默认基准 Target CPA (若广告活动未配置)
    public static final BigDecimal DEFAULT_TARGET_CPA = BigDecimal.valueOf(15.00);

    @Autowired
    public DeepBiddingOptimizer(
            DeepCtrCvrInferenceEngine inferenceEngine,
            DynamicBidShadingEngine shadingEngine
    ) {
        this.inferenceEngine = inferenceEngine != null ? inferenceEngine : new DeepCtrCvrInferenceEngine();
        this.shadingEngine = shadingEngine != null ? shadingEngine : new DynamicBidShadingEngine();
    }

    public DeepBiddingOptimizer() {
        this(new DeepCtrCvrInferenceEngine(), new DynamicBidShadingEngine());
    }

    /**
     * 计算面向单次竞价机会的最优深度出价决策
     *
     * @param context        流量环境上下文
     * @param campaign       候选广告活动
     * @param targetCpa      广告主目标 CPA
     * @param floorPrice     媒体底价
     * @param marketSegment  市场画像切片分段
     * @return 最终出价决策
     */
    public DeepBiddingDecision optimizeBid(
            TrafficContext context,
            Campaign campaign,
            BigDecimal targetCpa,
            BigDecimal floorPrice,
            String marketSegment
    ) {
        BigDecimal effectiveCpa = (targetCpa != null && targetCpa.signum() > 0)
                ? targetCpa
                : DEFAULT_TARGET_CPA;
        BigDecimal effectiveFloor = (floorPrice != null && floorPrice.signum() > 0)
                ? floorPrice
                : BigDecimal.ZERO;

        // 1. 深度模型前向推断 (pCTR & pCVR)
        DeepCtrCvrInferenceEngine.PredictionResult prediction = inferenceEngine.predict(context, campaign, effectiveFloor);

        // 2. 计算期望真实价值 Valuation (eCPM)
        BigDecimal rawValuation = prediction.calculateExpectedEcpm(effectiveCpa)
                .setScale(4, RoundingMode.HALF_UP);

        // 若真值低于底价，放弃竞价或返回底价
        if (rawValuation.compareTo(effectiveFloor) < 0) {
            return new DeepBiddingDecision(
                    BigDecimal.ZERO,
                    prediction.pCtr(),
                    prediction.pCvr(),
                    rawValuation,
                    1.0,
                    DecisionReason.BELOW_FLOOR_ABORT
            );
        }

        // 3. 驱动第一价格拍卖 Bid Shading
        DynamicBidShadingEngine.ShadingResult shading = shadingEngine.calculateShadedBid(
                rawValuation,
                effectiveFloor,
                marketSegment
        );

        BigDecimal bid = shading.shadedBid();
        DecisionReason reason = DecisionReason.OPTIMAL_SHADED;

        // 4. 底价安全护栏：出价不得低于底价
        if (bid.compareTo(effectiveFloor) < 0) {
            bid = effectiveFloor;
            reason = DecisionReason.CLAMPED_FLOOR;
        }

        // 5. 最高限价安全护栏：出价不得超过保护上限
        if (bid.compareTo(DEFAULT_MAX_BID_GUARD) > 0) {
            bid = DEFAULT_MAX_BID_GUARD;
            reason = DecisionReason.CLAMPED_MAX_BID;
        }

        return new DeepBiddingDecision(
                bid.setScale(4, RoundingMode.HALF_UP),
                prediction.pCtr(),
                prediction.pCvr(),
                rawValuation,
                shading.shadingRatio(),
                reason
        );
    }

    public enum DecisionReason {
        OPTIMAL_SHADED,
        CLAMPED_FLOOR,
        CLAMPED_MAX_BID,
        BELOW_FLOOR_ABORT
    }

    public record DeepBiddingDecision(
            BigDecimal bidAmount,
            double pCtr,
            double pCvr,
            BigDecimal rawValuation,
            double shadingRatio,
            DecisionReason reason
    ) {}
}
