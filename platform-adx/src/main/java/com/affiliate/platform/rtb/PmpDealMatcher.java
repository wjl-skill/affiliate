package com.affiliate.platform.rtb;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OpenRTB 2.5 私有交易市场协议与 Deal ID 撮合引擎 (PMP Deal Matcher)
 * <p>
 * 商业级 ADX（Google AdX / Magnite / OpenX）核心私有交易撮合组件：
 * 1. 深度解析请求中 `imp.pmp.deals` 私有协议条款；
 * 2. 支持三大私有交易模式：
 *    - PROGRAMMATIC_GUARANTEED (PG 保量合约，最高优先级)
 *    - PREFERRED_DEAL (优先交易，协商固定底价)
 *    - PRIVATE_AUCTION (私有竞价，限定席位白名单竞争)
 * 3. 校验席位白名单 (wseat) 与 Deal 专属底价门槛；
 * 4. 判定报价的 Deal 履约层级，赋予优先清算特权。
 */
@Component
public class PmpDealMatcher {

    public enum DealType {
        /** Tier 1: 合约保量 */
        PROGRAMMATIC_GUARANTEED(100),
        /** Tier 2: 优先洽购固定价 */
        PREFERRED_DEAL(80),
        /** Tier 3: 白名单私有竞价 */
        PRIVATE_AUCTION(50);

        private final int priorityWeight;

        DealType(int priorityWeight) {
            this.priorityWeight = priorityWeight;
        }

        public int getPriorityWeight() {
            return priorityWeight;
        }
    }

    /**
     * 私有交易协议实体
     */
    public record RegisteredDeal(
            String dealId,
            String advertiserId,
            DealType dealType,
            BigDecimal floorPrice,
            Set<String> allowedSeats,
            boolean active
    ) {
        public RegisteredDeal {
            allowedSeats = allowedSeats == null ? Set.of() : Set.copyOf(allowedSeats);
        }

        public boolean allowsSeat(String seat) {
            if (allowedSeats.isEmpty()) return true;
            return seat != null && allowedSeats.contains(seat);
        }
    }

    // 内存注册的 Deal 映射：Key 为 dealId
    private final ConcurrentMap<String, RegisteredDeal> registeredDeals = new ConcurrentHashMap<>();

    public void registerDeal(RegisteredDeal deal) {
        if (deal != null && deal.dealId() != null) {
            registeredDeals.put(deal.dealId(), deal);
        }
    }

    public Optional<RegisteredDeal> getDeal(String dealId) {
        if (dealId == null) return Optional.empty();
        return Optional.ofNullable(registeredDeals.get(dealId));
    }

    public void removeDeal(String dealId) {
        if (dealId != null) {
            registeredDeals.remove(dealId);
        }
    }

    public void clearDeals() {
        registeredDeals.clear();
    }

    /**
     * 校验买方提交的报价是否符合指定的 Deal 要求
     *
     * @param dealId   私有 Deal 标识
     * @param buyerSeat 买方 DSP 席位编码
     * @param bidPrice 买方出价
     * @return 校验匹配结果
     */
    public DealMatchResult matchDeal(String dealId, String buyerSeat, BigDecimal bidPrice) {
        if (dealId == null || dealId.isBlank()) {
            return DealMatchResult.notPmp();
        }

        RegisteredDeal deal = registeredDeals.get(dealId);
        if (deal == null || !deal.active()) {
            return new DealMatchResult(false, null, "Deal not found or inactive", 0);
        }

        // 席位白名单检查
        if (!deal.allowsSeat(buyerSeat)) {
            return new DealMatchResult(false, deal, "Buyer seat " + buyerSeat + " not authorized for this deal", 0);
        }

        // 出价门槛检查
        if (bidPrice == null || bidPrice.compareTo(deal.floorPrice()) < 0) {
            return new DealMatchResult(false, deal, "Bid price " + bidPrice + " is below deal floor " + deal.floorPrice(), 0);
        }

        return new DealMatchResult(true, deal, "Matched successfully", deal.dealType().getPriorityWeight());
    }

    public record DealMatchResult(
            boolean isMatched,
            RegisteredDeal deal,
            String message,
            int priority
    ) {
        public static DealMatchResult notPmp() {
            return new DealMatchResult(false, null, "Open Auction", 0);
        }
    }
}
