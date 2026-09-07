package com.affiliate.platform.rtb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * 实时竞价拍卖清算引擎 (RTB Auction Clearing Engine)
 * <p>
 * 支持业界核心清算模式：
 * 1. FIRST_PRICE（第一价清算）：胜出者按照其自身最高出价结算；
 * 2. SECOND_PRICE（第二价 Vickrey 拍卖清算）：胜出者以次高出价 + 0.01 美元（且不低于底价）结算，若仅有一位胜出者则以底价结算；
 * 3. DEAL_CLEARING（私有交易市场 PMP 清算）：遵循专属保量或保价协议 Deal Floor 清算。
 */
public class AuctionClearingEngine {

    public enum AuctionType {
        /** 第一价拍卖：按最高出价清算 */
        FIRST_PRICE(1),
        /** 第二价拍卖：按次高出价加一分钱清算 */
        SECOND_PRICE(2);

        private final int code;

        AuctionType(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    public record CandidateBid(String bidderId, String bidId, double price, String dealId) {}

    public record ClearingResult(
            boolean hasWinner,
            CandidateBid winner,
            double clearingPrice,
            AuctionType auctionType
    ) {}

    /**
     * 执行竞价清算裁决
     *
     * @param candidates  各买方出价候选列表
     * @param floorPrice  媒体底价
     * @param auctionType 拍卖类型 (第一价 / 第二价)
     * @param pmpDealId   请求中指定的私有 Deal ID（可选）
     * @return 最终清算结果
     */
    public ClearingResult clear(
            List<CandidateBid> candidates,
            double floorPrice,
            AuctionType auctionType,
            String pmpDealId
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new ClearingResult(false, null, 0.0, auctionType);
        }

        // 1. 若为私有交易 PMP，优先匹配符合 Deal ID 的出价
        if (pmpDealId != null && !pmpDealId.isBlank()) {
            List<CandidateBid> dealBids = candidates.stream()
                    .filter(b -> pmpDealId.equalsIgnoreCase(b.dealId()))
                    .filter(b -> b.price() >= floorPrice)
                    .sorted(Comparator.comparingDouble(CandidateBid::price).reversed())
                    .toList();

            if (!dealBids.isEmpty()) {
                CandidateBid dealWinner = dealBids.get(0);
                return new ClearingResult(true, dealWinner, dealWinner.price(), AuctionType.FIRST_PRICE);
            }
        }

        // 2. 公开竞价池：过滤低于底价的出价并按金额从高到低排序
        List<CandidateBid> validBids = candidates.stream()
                .filter(b -> b.price() >= floorPrice)
                .sorted(Comparator.comparingDouble(CandidateBid::price).reversed())
                .toList();

        if (validBids.isEmpty()) {
            return new ClearingResult(false, null, 0.0, auctionType);
        }

        CandidateBid winner = validBids.get(0);

        // 3. 第一价清算：以最高价成交
        if (auctionType == AuctionType.FIRST_PRICE) {
            return new ClearingResult(true, winner, winner.price(), AuctionType.FIRST_PRICE);
        }

        // 4. 第二价 Vickrey 清算：
        // 若有多个出价高于底价，以次高价 + 0.01 结算；若仅有一个出价高于底价，以底价结算
        double clearing;
        if (validBids.size() > 1) {
            double secondPrice = validBids.get(1).price();
            clearing = Math.max(floorPrice, secondPrice + 0.01);
        } else {
            clearing = floorPrice;
        }
        clearing = Math.min(clearing, winner.price()); // 绝不高于最高出价自身

        // 保留 4 位小数精度
        clearing = BigDecimal.valueOf(clearing).setScale(4, RoundingMode.HALF_UP).doubleValue();

        return new ClearingResult(true, winner, clearing, AuctionType.SECOND_PRICE);
    }
}
