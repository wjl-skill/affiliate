package com.affiliate.platform.ssp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 供给方聚合分发与竞价仲裁服务 (SSP Mediation & Header Bidding Service)
 * <p>
 * 支持主流媒体分发模式：
 * 1. Header Bidding（头部竞价并发聚合）：多渠道统一竞价比价；
 * 2. Waterfall（分层瀑布流）：按优先级梯队串行询价；
 * 3. Backfill（底价流拍保底填充）：当全网出价未达硬底价时，平滑切换至自营或保底广告源。
 */
@Service
public class MediationService {

    /**
     * 外部渠道或 DSP 报价分录
     *
     * @param bidderId 渠道标识（如 "DSP_A", "Google_AdX"）
     * @param price    报价金额（CPM USD）
     * @param adHtml   广告渲染代码或物料链接
     */
    public record BidCandidate(String bidderId, BigDecimal price, String adHtml) {}

    /**
     * 仲裁最终胜出结果
     */
    public record MediationResult(
            boolean isBackfill,
            String winnerBidderId,
            BigDecimal winningPrice,
            String renderContent
    ) {}

    /**
     * 执行头部竞价聚合仲裁
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
        if (bids == null || bids.isEmpty()) {
            return new MediationResult(true, "backfill", hardFloor, backfill);
        }

        // 筛选出达到硬底价的最高出价者
        Optional<BidCandidate> topBid = bids.stream()
                .filter(b -> b.price() != null && b.price().compareTo(hardFloor) >= 0)
                .max(Comparator.comparing(BidCandidate::price));

        if (topBid.isPresent()) {
            BidCandidate winner = topBid.get();
            return new MediationResult(false, winner.bidderId(), winner.price(), winner.adHtml());
        }

        // 全网流拍，触发保底兜底
        return new MediationResult(true, "backfill", hardFloor, backfill);
    }
}
