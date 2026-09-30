package com.affiliate.platform.ssp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 供给方聚合分发与统一竞价仲裁流水线服务 (SSP Unified Auction & Mediation Pipeline Service)
 * <p>
 * 商业级媒体收益变现（Google Ad Manager / AppLovin MAX / Magnite）核心出清流水线：
 * 1. Tier 1: Programmatic Guaranteed (PG 保量合约) -> 绝对最高排期优先级，保量直达；
 * 2. Tier 2: Preferred Deals (PMP 优先交易) -> 固定协商底价，优先比价撮合；
 * 3. Tier 3: Unified Open Auction + Header Bidding (统一头部竞价) -> 多渠道实时并行，软硬双底价自适应出清；
 * 4. Tier 4: House Ads / Backfill (底价流拍兜底) -> 无有效商业买家时 100% 填充媒体自营广告，消灭展示留白。
 */
@Service
public class MediationService {

    /**
     * 竞价层级枚举
     */
    public enum AuctionTier {
        /** Tier 1: 合约保量 */
        PROGRAMMATIC_GUARANTEED,
        /** Tier 2: 私有交易优先洽购 */
        PREFERRED_DEAL,
        /** Tier 3: 头部竞价与公开实时竞价 */
        OPEN_HEADER_BIDDING,
        /** Tier 4: 媒体自营保底填充 */
        HOUSE_BACKFILL
    }

    /**
     * 外部渠道或 DSP 报价分录
     */
    public record BidCandidate(String bidderId, BigDecimal price, String adHtml) {}

    /**
     * PMP 私有交易分录
     */
    public record PreferredDealBid(String dealId, String bidderId, BigDecimal agreedPrice, String adHtml) {}

    /**
     * 保量合约活动
     */
    public record GuaranteedContract(String contractId, String advertiserId, BigDecimal cpmValue, String adHtml) {}

    /**
     * 仲裁最终胜出结果
     */
    public record MediationResult(
            boolean isBackfill,
            String winnerBidderId,
            BigDecimal winningPrice,
            String renderContent,
            AuctionTier winningTier,
            String dealOrContractId
    ) {
        // 向后兼容构造器
        public MediationResult(boolean isBackfill, String winnerBidderId, BigDecimal winningPrice, String renderContent) {
            this(isBackfill, winnerBidderId, winningPrice, renderContent,
                    isBackfill ? AuctionTier.HOUSE_BACKFILL : AuctionTier.OPEN_HEADER_BIDDING, null);
        }
    }

    /**
     * 执行工业级四层统一混合竞价仲裁流水线 (Unified 4-Tier Auction Clearing)
     *
     * @param guaranteedContract 候选保量合约（若当前有排期满足则优先执行）
     * @param preferredDeals     候选 PMP 优先交易报价
     * @param headerBids         Header Bidding 各渠道汇总竞价
     * @param floorPriceResult   动态收益引擎计算的有效软硬底价
     * @param backfillHtml       保底兜底物料
     * @return 最终清算胜出结果
     */
    public MediationResult arbitrateUnifiedAuction(
            GuaranteedContract guaranteedContract,
            List<PreferredDealBid> preferredDeals,
            List<BidCandidate> headerBids,
            DynamicYieldManager.FloorPriceResult floorPriceResult,
            String backfillHtml
    ) {
        BigDecimal hardFloor = floorPriceResult != null && floorPriceResult.hardFloor() != null
                ? floorPriceResult.hardFloor()
                : BigDecimal.ZERO;

        BigDecimal softFloor = floorPriceResult != null && floorPriceResult.softFloor() != null
                ? floorPriceResult.softFloor()
                : hardFloor;

        // Tier 1: Programmatic Guaranteed (PG) 优先出清
        if (guaranteedContract != null && guaranteedContract.adHtml() != null && !guaranteedContract.adHtml().isBlank()) {
            return new MediationResult(
                    false,
                    "Guaranteed_" + guaranteedContract.advertiserId(),
                    guaranteedContract.cpmValue() != null ? guaranteedContract.cpmValue() : BigDecimal.ZERO,
                    guaranteedContract.adHtml(),
                    AuctionTier.PROGRAMMATIC_GUARANTEED,
                    guaranteedContract.contractId()
            );
        }

        // Tier 2: Preferred Deals (PMP) 优先洽购
        if (preferredDeals != null && !preferredDeals.isEmpty()) {
            Optional<PreferredDealBid> matchedDeal = preferredDeals.stream()
                    .filter(d -> d.agreedPrice() != null && d.agreedPrice().compareTo(hardFloor) >= 0)
                    .max(Comparator.comparing(PreferredDealBid::agreedPrice));

            if (matchedDeal.isPresent()) {
                PreferredDealBid deal = matchedDeal.get();
                return new MediationResult(
                        false,
                        deal.bidderId(),
                        deal.agreedPrice(),
                        deal.adHtml(),
                        AuctionTier.PREFERRED_DEAL,
                        deal.dealId()
                );
            }
        }

        // Tier 3: Unified Open Auction + Header Bidding
        if (headerBids != null && !headerBids.isEmpty()) {
            // 筛选达硬底价候选集，按出价降序排列
            List<BidCandidate> eligibleBids = headerBids.stream()
                    .filter(b -> b.price() != null && b.price().compareTo(hardFloor) >= 0)
                    .sorted((a, b) -> b.price().compareTo(a.price()))
                    .toList();

            if (!eligibleBids.isEmpty()) {
                BidCandidate winner = eligibleBids.get(0);
                BigDecimal finalClearingPrice;

                // 软硬双底价智能出清：
                // 若最高出价达到软底价，按第一价格自身结算
                // 若介于硬底价与软底价之间，按软底价或次高价出清以提升媒体收益
                if (winner.price().compareTo(softFloor) >= 0) {
                    finalClearingPrice = winner.price();
                } else if (eligibleBids.size() > 1) {
                    BigDecimal secondPrice = eligibleBids.get(1).price();
                    finalClearingPrice = secondPrice.max(hardFloor);
                } else {
                    finalClearingPrice = hardFloor;
                }

                return new MediationResult(
                        false,
                        winner.bidderId(),
                        finalClearingPrice,
                        winner.adHtml(),
                        AuctionTier.OPEN_HEADER_BIDDING,
                        null
                );
            }
        }

        // Tier 4: 保底兜底 (House Ads Backfill)
        return new MediationResult(
                true,
                "backfill",
                hardFloor,
                backfillHtml != null ? backfillHtml : "<div>House Default Ad</div>",
                AuctionTier.HOUSE_BACKFILL,
                null
        );
    }

    /**
     * 基础头部竞价聚合仲裁（向后完全兼容）
     *
     * @param bids      各渠道汇总的竞价列表
     * @param hardFloor 动态硬底价门槛
     * @param backfill  媒体保底广告物料
     * @return 最终仲裁结果
     */
    public MediationResult arbitrateHeaderBidding(
            List<BidCandidate> bids,
            BigDecimal hardFloor,
            String backfill
    ) {
        DynamicYieldManager.FloorPriceResult floor = new DynamicYieldManager.FloorPriceResult(hardFloor, hardFloor);
        return arbitrateUnifiedAuction(null, List.of(), bids, floor, backfill);
    }
}
