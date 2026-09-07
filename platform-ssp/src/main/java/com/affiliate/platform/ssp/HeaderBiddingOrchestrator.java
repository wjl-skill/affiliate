package com.affiliate.platform.ssp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Prebid 规范并发 Header Bidding 头部竞价编排器 (Header Bidding Orchestrator)
 * <p>
 * 商业级 SSP 与媒体变现平台（Magnite / OpenX / Prebid.org）的核心竞价分发组件：
 * 1. 废弃传统低效的串行瀑布流（Waterfall），向已配置的多个 DSP 需求源并发广播询价；
 * 2. 严格执行毫秒级超时熔断保护（tmax，如 50ms~100ms），保障媒体网页渲染极速加载；
 * 3. 统一执行广告位底价（Floor Price）拦截与统一竞价出清（Unified Auction Clearing）。
 */
@Service
public class HeaderBiddingOrchestrator {

    private final ExecutorService auctionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 执行并发头部竞价统一拍卖
     *
     * @param slotId       广告位标识
     * @param floorPrice   保留底价 (Reserve Floor)
     * @param candidateAdapters 参与本次竞价的候选 DSP 适配器清单
     * @param timeoutMs    媒体设定的硬超时时间 (ms)
     * @return 最终胜出出价与竞价统计指标
     */
    public HeaderBiddingAuctionResult orchestrateAuction(
            String slotId,
            BigDecimal floorPrice,
            List<DemandPartnerAdapter> candidateAdapters,
            long timeoutMs
    ) {
        if (candidateAdapters == null || candidateAdapters.isEmpty()) {
            return HeaderBiddingAuctionResult.noBids(slotId, 0, 0);
        }

        Instant start = Instant.now();
        List<CompletableFuture<BidOffer>> futures = new ArrayList<>();

        for (DemandPartnerAdapter adapter : candidateAdapters) {
            CompletableFuture<BidOffer> future = CompletableFuture.supplyAsync(
                    () -> adapter.requestBid(slotId), auctionExecutor
            );
            futures.add(future);
        }

        // 并发聚合与超时截断
        List<BidOffer> validOffers = new ArrayList<>();
        int timedOutCount = 0;

        for (CompletableFuture<BidOffer> f : futures) {
            try {
                // 计算剩余可用超时预算
                long elapsed = Duration.between(start, Instant.now()).toMillis();
                long remaining = Math.max(1, timeoutMs - elapsed);

                BidOffer offer = f.get(remaining, TimeUnit.MILLISECONDS);
                if (offer != null && offer.cpmPrice() != null && offer.cpmPrice().signum() > 0) {
                    validOffers.add(offer);
                }
            } catch (TimeoutException e) {
                timedOutCount++;
                f.cancel(true);
            } catch (Exception e) {
                // 异常忽略并降级
            }
        }

        BigDecimal floor = floorPrice != null ? floorPrice : BigDecimal.ZERO;

        // 底价过滤并按 CPM 降序排序
        List<BidOffer> eligible = validOffers.stream()
                .filter(o -> o.cpmPrice().compareTo(floor) >= 0)
                .sorted((a, b) -> b.cpmPrice().compareTo(a.cpmPrice()))
                .toList();

        long durationMs = Duration.between(start, Instant.now()).toMillis();

        if (eligible.isEmpty()) {
            return new HeaderBiddingAuctionResult(slotId, false, null, floor, validOffers.size(), timedOutCount, durationMs);
        }

        BidOffer winner = eligible.get(0);
        // 第一价格出清：获胜价格即最高出价
        BigDecimal clearingPrice = winner.cpmPrice();

        return new HeaderBiddingAuctionResult(
                slotId,
                true,
                winner,
                clearingPrice,
                validOffers.size(),
                timedOutCount,
                durationMs
        );
    }

    public interface DemandPartnerAdapter {
        String partnerId();
        BidOffer requestBid(String slotId);
    }

    public record BidOffer(
            String bidderId,
            String creativeId,
            BigDecimal cpmPrice,
            String adMarkup,
            String currency
    ) {}

    public record HeaderBiddingAuctionResult(
            String slotId,
            boolean hasWinner,
            BidOffer winningBid,
            BigDecimal clearingPrice,
            int respondedBidders,
            int timedOutBidders,
            long executionTimeMs
    ) {
        public static HeaderBiddingAuctionResult noBids(String slotId, int responded, int timedOut) {
            return new HeaderBiddingAuctionResult(slotId, false, null, BigDecimal.ZERO, responded, timedOut, 0);
        }
    }
}
