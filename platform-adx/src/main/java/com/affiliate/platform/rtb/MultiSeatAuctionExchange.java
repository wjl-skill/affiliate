package com.affiliate.platform.rtb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * ADX 多边撮合交易市场引擎 (Multilateral Ad Exchange Engine)
 * <p>
 * 连接媒体供给方 (SSP/Publishers) 与外部多家需求方 (DSP/Seats) 的核心撮合出清中枢：
 * 1. 注册管理外部 DSP 席位适配器清单 (SeatBidderAdapter)；
 * 2. 毫秒级并发广播竞价请求，按超时预算 (tmax) 严格截断；
 * 3. 协同 {@link PmpDealMatcher} 实现 PMP 私有交易与公开拍卖的分层加权竞胜；
 * 4. 支持 First-Price 与 Second-Price (Vickrey + 0.01) 统一多边出清。
 */
@Service
public class MultiSeatAuctionExchange {

    private final ExecutorService exchangeExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final PmpDealMatcher pmpMatcher;
    private final List<SeatBidderAdapter> registeredAdapters = new CopyOnWriteArrayList<>();

    @Autowired
    public MultiSeatAuctionExchange(PmpDealMatcher pmpMatcher) {
        this.pmpMatcher = pmpMatcher != null ? pmpMatcher : new PmpDealMatcher();
    }

    public MultiSeatAuctionExchange() {
        this(new PmpDealMatcher());
    }

    public void registerSeat(SeatBidderAdapter adapter) {
        if (adapter != null) {
            registeredAdapters.add(adapter);
        }
    }

    public void unregisterSeat(String seatId) {
        registeredAdapters.removeIf(a -> a.seatId().equalsIgnoreCase(seatId));
    }

    public void clearSeats() {
        registeredAdapters.clear();
    }

    public List<SeatBidderAdapter> getRegisteredSeats() {
        return Collections.unmodifiableList(registeredAdapters);
    }

    /**
     * 外部 DSP 席位适配器接口
     */
    public interface SeatBidderAdapter {
        String seatId();
        Optional<OpenRtb.BidResponse> requestBids(OpenRtb.BidRequest request);
    }

    /**
     * 综合竞价候选分录
     */
    public record EvaluatedSeatBid(
            String seatId,
            OpenRtb.Bid rawBid,
            BigDecimal price,
            PmpDealMatcher.DealMatchResult pmpResult,
            int priorityScore
    ) {}

    /**
     * 多边撮合最终清算结果
     */
    public record ExchangeClearingResult(
            boolean hasWinner,
            String winningSeatId,
            OpenRtb.Bid winningBid,
            BigDecimal clearingPrice,
            String clearingMode,
            int totalBidsReceived,
            long executionTimeMs
    ) {
        public static ExchangeClearingResult noWinner(long timeMs) {
            return new ExchangeClearingResult(false, null, null, BigDecimal.ZERO, "NO_BIDS", 0, timeMs);
        }
    }

    /**
     * 执行多席位并发拍卖与多边统一出清
     *
     * @param request       OpenRTB 2.5 竞价请求
     * @param reserveFloor  广告位保留底价 (Reserve Floor Price)
     * @param isSecondPrice 是否按第二价出清
     * @return 最终出清结果
     */
    public ExchangeClearingResult conductExchangeAuction(
            OpenRtb.BidRequest request,
            BigDecimal reserveFloor,
            boolean isSecondPrice
    ) {
        Instant start = Instant.now();
        if (request == null || registeredAdapters.isEmpty()) {
            return ExchangeClearingResult.noWinner(0);
        }

        int tmax = (request.tmax() != null && request.tmax() > 0) ? request.tmax() : 20;
        // 为 ADX 聚合出清与网络封包预留 3ms 缓冲
        long timeoutBudgetMs = Math.max(2, tmax - 3);

        // 1. 并发向所有已注册 DSP 席位广播请求
        List<CompletableFuture<Optional<OpenRtb.BidResponse>>> futures = new ArrayList<>();
        Map<CompletableFuture<Optional<OpenRtb.BidResponse>>, String> futureToSeat = new LinkedHashMap<>();

        for (SeatBidderAdapter adapter : registeredAdapters) {
            CompletableFuture<Optional<OpenRtb.BidResponse>> f = CompletableFuture.supplyAsync(
                    () -> adapter.requestBids(request), exchangeExecutor
            );
            futures.add(f);
            futureToSeat.put(f, adapter.seatId());
        }

        // 2. 收集各席位返回的出价分录
        List<EvaluatedSeatBid> candidateBids = new ArrayList<>();
        for (Map.Entry<CompletableFuture<Optional<OpenRtb.BidResponse>>, String> entry : futureToSeat.entrySet()) {
            CompletableFuture<Optional<OpenRtb.BidResponse>> future = entry.getKey();
            String seatId = entry.getValue();

            try {
                long elapsed = Duration.between(start, Instant.now()).toMillis();
                long remaining = Math.max(1, timeoutBudgetMs - elapsed);

                Optional<OpenRtb.BidResponse> respOpt = future.get(remaining, TimeUnit.MILLISECONDS);
                if (respOpt.isPresent() && respOpt.get().seatbid() != null) {
                    for (OpenRtb.SeatBid sb : respOpt.get().seatbid()) {
                        String currentSeat = (sb.seat() != null && !sb.seat().isBlank()) ? sb.seat() : seatId;
                        if (sb.bid() != null) {
                            for (OpenRtb.Bid b : sb.bid()) {
                                if (b.price() > 0) {
                                    BigDecimal price = BigDecimal.valueOf(b.price());
                                    // 校验底价
                                    if (reserveFloor != null && price.compareTo(reserveFloor) < 0) {
                                        continue;
                                    }
                                    // PMP Deal 校验与优先级打分
                                    PmpDealMatcher.DealMatchResult pmpRes = pmpMatcher.matchDeal(
                                            b.dealid(), currentSeat, price
                                    );
                                    int score = pmpRes.priority();
                                    candidateBids.add(new EvaluatedSeatBid(currentSeat, b, price, pmpRes, score));
                                }
                            }
                        }
                    }
                }
            } catch (TimeoutException te) {
                future.cancel(true);
            } catch (Exception ignored) {
                // 席位异常降级忽略
            }
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        if (candidateBids.isEmpty()) {
            return ExchangeClearingResult.noWinner(durationMs);
        }

        // 3. 排序规则：优先比 Deal 层级（score 越大越优先），同层级比价格（price 越高越优先）
        candidateBids.sort((a, b) -> {
            if (a.priorityScore() != b.priorityScore()) {
                return Integer.compare(b.priorityScore(), a.priorityScore());
            }
            return b.price().compareTo(a.price());
        });

        EvaluatedSeatBid winner = candidateBids.get(0);
        BigDecimal clearingPrice;
        String clearingMode;

        if (winner.pmpResult().isMatched() && winner.pmpResult().deal().dealType() == PmpDealMatcher.DealType.PREFERRED_DEAL) {
            // Preferred Deal：固定底价出清
            clearingPrice = winner.pmpResult().deal().floorPrice();
            clearingMode = "PREFERRED_DEAL_FIXED";
        } else if (isSecondPrice) {
            // 第二价格清算：次高价 + 0.01（同层级内）或底价
            if (candidateBids.size() > 1 && candidateBids.get(1).priorityScore() == winner.priorityScore()) {
                clearingPrice = candidateBids.get(1).price().add(new BigDecimal("0.01")).min(winner.price());
            } else if (reserveFloor != null) {
                clearingPrice = reserveFloor;
            } else {
                clearingPrice = winner.price();
            }
            clearingMode = "SECOND_PRICE_VICKREY";
        } else {
            // 第一价格清算
            clearingPrice = winner.price();
            clearingMode = "FIRST_PRICE";
        }

        return new ExchangeClearingResult(
                true,
                winner.seatId(),
                winner.rawBid(),
                clearingPrice,
                clearingMode,
                candidateBids.size(),
                durationMs
        );
    }
}
