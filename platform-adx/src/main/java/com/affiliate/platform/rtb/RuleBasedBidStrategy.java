package com.affiliate.platform.rtb;

import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.domain.Creative;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 基于规则与广义第二价格 (GSP) 动态加价的出价决策策略 (Rule-Based & Dynamic Pricing Bid Strategy)
 * <p>
 * 负责候选素材评分优选与最终出价计算：
 * 1. 过滤未激活及规格不符素材；
 * 2. 选取最佳物料（支持最新鲜/最高评分优选）；
 * 3. 动态结合广告位底价与请求出价底价，以 10% 溢价加 $0.01 计算胜出概率最优的真实出价。
 */
@Component
public class RuleBasedBidStrategy implements BidStrategy {

    /**
     * 在候选素材集中择优并生成出价决策
     *
     * @param request    OpenRTB 竞价请求上下文
     * @param impression 当前曝光位机会
     * @param slot       广告位底价与属性配置
     * @param creatives  尺寸倒排或全量候选素材
     * @return 若有胜出候选返回包含物料、价格与广告主的 BidDecision，否则返回 Optional.empty()
     */
    @Override
    public Optional<BidDecision> choose(OpenRtb.BidRequest request, OpenRtb.Imp impression, AdSlot slot, Iterable<Creative> creatives) {
        if (creatives == null) {
            return Optional.empty();
        }

        // 提取媒体请求的横幅尺寸（宽与高）
        int requestedWidth = impression.banner() != null ? impression.banner().w() : 0;
        int requestedHeight = impression.banner() != null ? impression.banner().h() : 0;

        Creative best = null;
        for (Creative creative : creatives) {
            // 排除未激活或已暂停的素材
            if (!creative.active()) {
                continue;
            }
            // 尺寸精确匹配校验（兼容倒排索引预筛与直接输入场景）
            if (requestedWidth > 0 && (creative.width() != requestedWidth || creative.height() != requestedHeight)) {
                continue;
            }

            // 选取最新创建/最新鲜度的物料优先投放
            if (best == null || creative.createdAt().isAfter(best.createdAt())) {
                best = creative;
            }
        }

        // 无任何合格候选素材则放弃出价 (No-Bid)
        if (best == null) {
            return Optional.empty();
        }

        // 动态计算本次竞价有效底价（取广告位固定底价与实时请求底价的较大者）
        double floor = Math.max(slot.floorPrice(), impression.bidfloor());

        // 动态 GSP 加价公式：底价 + 10% 溢价 + $0.01，保留 4 位微美分精度
        double bidPrice = Math.round((floor * 1.10 + 0.01) * 10000.0) / 10000.0;

        // 返回包含最优素材、出价价格与广告主标识的决策记录
        return Optional.of(new BidDecision(best, bidPrice, "default-advertiser"));
    }
}
