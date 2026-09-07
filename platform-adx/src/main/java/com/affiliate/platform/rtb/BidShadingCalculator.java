package com.affiliate.platform.rtb;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时竞价出价折价优化器 (First-Price RTB Bid Shading Calculator)
 * <p>
 * 商业级 DSP 与 ADX（The Trade Desk / AppLovin）在第一价格竞价（First-Price Auction）中的核心省钱算法：
 * 当 SSP 全面采用第一价格结算时，买方若直接以评估的最大价值（Max Valuation）出价，会导致严重的溢价损失。
 * Bid Shading 基于广告位的历史出清价格均值、胜出中位数及底价，在保证赢率的前提下智能折价出价，平均为广告主降低 15%~25% 的采购成本。
 */
@Service
public class BidShadingCalculator {

    // 历史广告位获胜出清参考价缓存：Key 为 slotId
    private final Map<String, HistoricalClearingProfile> slotProfiles = new ConcurrentHashMap<>();

    // 默认激进保胜率安全系数 (Safety Margin = 1.10，即在历史均价上浮 10% 确保获胜)
    private static final BigDecimal DEFAULT_SAFETY_MARGIN = new BigDecimal("1.10");

    /**
     * 计算折价后的优化出价 (Shaded Bid CPM)
     *
     * @param slotId      广告位标识
     * @param maxBid      买方愿意支付的最高价值上限 (Max Valuation CPM)
     * @param floorPrice  SSP 规定的竞价保留底价 (Reserve Floor Price)
     * @return 经智能折价后的最终实际出价
     */
    public BidShadingResult calculateShadedBid(String slotId, BigDecimal maxBid, BigDecimal floorPrice) {
        if (maxBid == null || maxBid.signum() <= 0) {
            return new BidShadingResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal floor = floorPrice != null && floorPrice.signum() > 0 ? floorPrice : BigDecimal.ZERO;

        // 若最高意向出价低于底价，无需折价，直接以底价参竞或放弃
        if (maxBid.compareTo(floor) <= 0) {
            return new BidShadingResult(maxBid, maxBid, BigDecimal.ZERO);
        }

        HistoricalClearingProfile profile = slotProfiles.get(slotId);
        BigDecimal shadedBid;

        if (profile != null && profile.sampleCount() >= 5) {
            // 模式 A: 基于历史出清分布进行经验折价
            // 估算获胜价格 = 历史平均出清价 * 1.10 安全边际
            BigDecimal estimatedClearing = profile.avgClearingPrice().multiply(DEFAULT_SAFETY_MARGIN);

            // Shaded Bid 位于 [floor, maxBid] 之间
            if (estimatedClearing.compareTo(floor) < 0) {
                shadedBid = floor.multiply(new BigDecimal("1.02")); // 略微高出底价 2%
            } else if (estimatedClearing.compareTo(maxBid) > 0) {
                shadedBid = maxBid; // 历史竞争极其激烈，不得不出最高价
            } else {
                shadedBid = estimatedClearing;
            }
        } else {
            // 模式 B: 冷启动无历史数据时，应用经典线性插值折价模型 (80% 均值折价法)
            // Shaded Bid = floor + (maxBid - floor) * 0.75
            BigDecimal spread = maxBid.subtract(floor);
            shadedBid = floor.add(spread.multiply(new BigDecimal("0.75")));
        }

        shadedBid = shadedBid.setScale(4, RoundingMode.HALF_UP);

        // 严格保障不低于底价且不超过最高出价
        if (shadedBid.compareTo(floor) < 0) shadedBid = floor;
        if (shadedBid.compareTo(maxBid) > 0) shadedBid = maxBid;

        // 计算节省比例 = (maxBid - shadedBid) / maxBid
        BigDecimal savings = maxBid.subtract(shadedBid)
                .divide(maxBid, 4, RoundingMode.HALF_UP);

        return new BidShadingResult(maxBid, shadedBid, savings);
    }

    /**
     * 胜出反馈：向样本库灌入最新的获胜出清价格以持续迭代优化
     */
    public void recordClearingFeedback(String slotId, BigDecimal winningPrice) {
        if (slotId == null || winningPrice == null || winningPrice.signum() <= 0) return;

        slotProfiles.compute(slotId, (k, existing) -> {
            if (existing == null) {
                return new HistoricalClearingProfile(winningPrice, 1);
            }
            // 增量动态滑动平均
            BigDecimal newAvg = existing.avgClearingPrice()
                    .multiply(BigDecimal.valueOf(existing.sampleCount()))
                    .add(winningPrice)
                    .divide(BigDecimal.valueOf(existing.sampleCount() + 1), 4, RoundingMode.HALF_UP);
            return new HistoricalClearingProfile(newAvg, existing.sampleCount() + 1);
        });
    }

    public record BidShadingResult(
            BigDecimal originalMaxBid,
            BigDecimal shadedBid,
            BigDecimal estimatedSavingsRatio
    ) {}

    private record HistoricalClearingProfile(
            BigDecimal avgClearingPrice,
            int sampleCount
    ) {}
}
