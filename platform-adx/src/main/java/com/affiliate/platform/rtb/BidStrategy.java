package com.affiliate.platform.rtb;

import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.domain.Creative;

import java.util.Optional;

/**
 * 竞价出价策略顶级抽象接口 (Bid Strategy Interface)
 * <p>
 * 在实时竞价 (RTB) 撮合流水线中，负责从满足尺寸与定向规则的候选素材集中评估优选，
 * 并结合广告位底价与受众质量分计算最终的出价决策。
 */
public interface BidStrategy {

    /**
     * 针对单次曝光机会进行出价决策
     *
     * @param request    OpenRTB 竞价请求上下文
     * @param impression 当前曝光机会 (Impression)
     * @param slot       广告位规格及底价配置
     * @param creatives  候选素材集合
     * @return 包含最优素材、出价价格与广告主标识的决策实体；无合适出价时返回 Optional.empty()
     */
    Optional<BidDecision> choose(OpenRtb.BidRequest request, OpenRtb.Imp impression, AdSlot slot, Iterable<Creative> creatives);

    /**
     * 出价决策结果记录实体 (Bid Decision Record)
     *
     * @param creative   最终选中的广告素材实体
     * @param price      计算产出的竞价出价金额 (CPM USD)
     * @param advertiser 胜出的广告主或投放计划唯一标识
     */
    record BidDecision(Creative creative, double price, String advertiser) {}
}
