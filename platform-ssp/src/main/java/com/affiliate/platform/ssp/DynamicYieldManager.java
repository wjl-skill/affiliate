package com.affiliate.platform.ssp;

import com.affiliate.platform.domain.AdSlot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 供给方动态收益与自适应底价决策引擎 (SSP Dynamic Yield & Adaptive Floor Price Manager)
 * <p>
 * 解决媒体端收益最大化（Yield Optimization）核心诉求：
 * 1. 在流量请求进入时，根据地理位置、终端设备类型与时段动态匹配最优软硬底价规则；
 * 2. 具备市场竞价自适应感知能力 (Market-Aware Adaptive Pricing)：
 *    根据近期竞价流拍率（Fill Rate）与出价强度，自适应微调硬底价（Hard Floor）与软底价（Soft Floor）；
 * 3. 兼顾高收益变现与媒体广告位填充率，防止 DSP 联手压价或底价过高导致大面积流拍。
 */
@Service
public class DynamicYieldManager {

    // 广告位专属底价规则列表：Key 为 slotId
    private final ConcurrentMap<String, List<FloorPriceRule>> rules = new ConcurrentHashMap<>();

    // 广告位实时竞价市场反馈画像：Key 为 slotId
    private final ConcurrentMap<String, MarketYieldStats> marketStatsMap = new ConcurrentHashMap<>();

    /**
     * 注册或更新广告位的动态底价规则
     */
    public void addRule(FloorPriceRule rule) {
        rules.computeIfAbsent(rule.slotId(), k -> new ArrayList<>()).add(rule);
    }

    /**
     * 清空广告位规则
     */
    public void clearRules(String slotId) {
        rules.remove(slotId);
    }

    /**
     * 计算该次曝光请求的实际生效底价（基于配置规则）
     *
     * @param slot       广告位基础配置
     * @param country    请求来源国家 (如 "US", "CN")
     * @param deviceType 请求设备类型 (如 1 手机, 4 桌面PC)
     * @param hour       请求时刻小时 (0..23)
     * @return 计算后的底价结果对象（包含有效硬底价与软底价）
     */
    public FloorPriceResult resolveFloor(AdSlot slot, String country, int deviceType, int hour) {
        BigDecimal defaultFloor = BigDecimal.valueOf(slot.floorPrice());
        List<FloorPriceRule> slotRules = rules.get(slot.id());

        if (slotRules == null || slotRules.isEmpty()) {
            return new FloorPriceResult(defaultFloor, defaultFloor);
        }

        // 匹配最具体的规则（若多条匹配则取最高底价以保障媒体收益）
        BigDecimal maxHard = defaultFloor;
        BigDecimal maxSoft = defaultFloor;

        for (FloorPriceRule rule : slotRules) {
            if (rule.matches(country, deviceType, hour)) {
                if (rule.hardFloor().compareTo(maxHard) > 0) {
                    maxHard = rule.hardFloor();
                }
                if (rule.softFloor().compareTo(maxSoft) > 0) {
                    maxSoft = rule.softFloor();
                }
            }
        }

        return new FloorPriceResult(maxHard, maxSoft);
    }

    /**
     * 自适应动态底价计算 (Adaptive Yield Optimization)
     * <p>
     * 结合规则引擎与实时市场成交率闭环微调：
     * - 当流拍率严重 (> 50%) 时，硬底价自适应打折下调 (最大降幅 30%) 以提高填充率；
     * - 当填充率极高 (> 80%) 且多买家活跃时，硬底价与软底价自适应抬升 (最大增幅 50%) 攫取更多媒体收益。
     */
    public FloorPriceResult resolveAdaptiveFloor(AdSlot slot, String country, int deviceType, int hour) {
        FloorPriceResult baseResult = resolveFloor(slot, country, deviceType, hour);
        MarketYieldStats stats = marketStatsMap.get(slot.id());

        if (stats == null || stats.totalAuctions.get() < 5) {
            return baseResult;
        }

        double fillRate = stats.calculateFillRate();
        double multiplier = 1.0;

        if (fillRate < 0.50) {
            // 流拍严重，自适应折价：最低 0.70x
            multiplier = Math.max(0.70, 0.70 + (fillRate / 0.50) * 0.30);
        } else if (fillRate > 0.80) {
            // 需求旺盛，自适应提价：最高 1.50x
            multiplier = Math.min(1.50, 1.0 + ((fillRate - 0.80) / 0.20) * 0.50);
        }

        BigDecimal adjustedHard = baseResult.hardFloor()
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal adjustedSoft = baseResult.softFloor()
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(4, RoundingMode.HALF_UP);

        return new FloorPriceResult(adjustedHard, adjustedSoft);
    }

    /**
     * 记录拍卖出清反馈，更新该广告位的市场收益画像
     *
     * @param slotId      广告位标识
     * @param cleared     是否成功出清（未流拍）
     * @param winPrice    成交价格（若流拍可传 0）
     * @param bidderCount 参与竞价的买方数量
     */
    public void recordAuctionOutcome(String slotId, boolean cleared, double winPrice, int bidderCount) {
        if (slotId == null) return;
        MarketYieldStats stats = marketStatsMap.computeIfAbsent(slotId, k -> new MarketYieldStats());
        stats.record(cleared, winPrice, bidderCount);
    }

    public MarketYieldStats getStats(String slotId) {
        return marketStatsMap.get(slotId);
    }

    public void clearStats() {
        marketStatsMap.clear();
    }

    /**
     * 底价决策结果
     *
     * @param hardFloor 硬底价：低于此价格直接流拍
     * @param softFloor 软底价：高于此价格走第一价结算，介于两者之间走次高价竞价
     */
    public record FloorPriceResult(BigDecimal hardFloor, BigDecimal softFloor) {}

    /**
     * 广告位市场收益统计指标
     */
    public static class MarketYieldStats {
        public final AtomicLong totalAuctions = new AtomicLong(0);
        public final AtomicLong filledAuctions = new AtomicLong(0);
        public final AtomicLong totalBidders = new AtomicLong(0);
        private double totalRevenue = 0.0;

        public synchronized void record(boolean cleared, double price, int bidders) {
            totalAuctions.incrementAndGet();
            totalBidders.addAndGet(bidders);
            if (cleared) {
                filledAuctions.incrementAndGet();
                totalRevenue += price;
            }
        }

        public double calculateFillRate() {
            long total = totalAuctions.get();
            return total == 0 ? 0.0 : (double) filledAuctions.get() / total;
        }

        public synchronized double avgWinningPrice() {
            long filled = filledAuctions.get();
            return filled == 0 ? 0.0 : totalRevenue / filled;
        }
    }
}
