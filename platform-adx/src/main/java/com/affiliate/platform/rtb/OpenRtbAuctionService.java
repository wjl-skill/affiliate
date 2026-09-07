package com.affiliate.platform.rtb;

import com.affiliate.platform.budget.BudgetService;
import com.affiliate.platform.budget.FrequencyCapService;
import com.affiliate.platform.budget.LocalBudgetSliceService;
import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.domain.Auction;
import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.repository.Repository;
import com.affiliate.platform.service.AdSlotService;
import com.affiliate.platform.service.CreativeService;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OpenRTB 2.5 极速实时竞价拍卖服务 (High-Performance RTB Auction Service)
 * <p>
 * 遵循工业界 P99 < 20ms 的超低延迟 SLA 规范：
 * 1. 采用内存倒排索引 (CreativeInvertedIndex) 实现 O(1) 纳秒级素材检索；
 * 2. 采用两级本地配额切片 (LocalBudgetSliceService) 实现微秒级无网络 RTT 预算扣减；
 * 3. 采用基于 Redis 的滑动窗口频控避免骚扰与超频；
 * 4. 内置 15ms 请求级硬超时截断防护机制，预留 5ms 网络传输与序列化缓冲，绝不违约。
 */
@Service
public class OpenRtbAuctionService {

    // 媒体广告位服务
    private final AdSlotService slots;

    // 广告素材服务
    private final CreativeService creatives;

    // 竞价出价策略（支持底价剪枝与动态 GSP 加价）
    private final BidStrategy strategy;

    // 拍卖成交事实仓储
    private final Repository<Auction> auctions;

    // 全局预算服务
    private final BudgetService budgetService;

    // 分布式滑动窗口频控服务
    private final FrequencyCapService freqCapService;

    // HMAC 防篡改签名服务（用于追踪 Token 生成）
    private final HmacTokenService hmacService;

    // 本地两级预算切片服务
    private final LocalBudgetSliceService localBudgetService;

    // 内存多维倒排索引
    private final CreativeInvertedIndex creativeIndex;

    /**
     * 完整依赖注入构造器
     */
    @Autowired
    public OpenRtbAuctionService(
            AdSlotService slots,
            CreativeService creatives,
            BidStrategy strategy,
            Repository<Auction> auctions,
            BudgetService budgetService,
            FrequencyCapService freqCapService,
            HmacTokenService hmacService,
            LocalBudgetSliceService localBudgetService,
            CreativeInvertedIndex creativeIndex
    ) {
        this.slots = slots;
        this.creatives = creatives;
        this.strategy = strategy;
        this.auctions = auctions;
        this.budgetService = budgetService;
        this.freqCapService = freqCapService;
        this.hmacService = hmacService;
        this.localBudgetService = localBudgetService;
        this.creativeIndex = creativeIndex;
    }

    /**
     * 兼容性构造器（用于单测与默认装配场景）
     */
    public OpenRtbAuctionService(
            AdSlotService slots,
            CreativeService creatives,
            BidStrategy strategy,
            Repository<Auction> auctions,
            BudgetService budgetService,
            FrequencyCapService freqCapService,
            HmacTokenService hmacService
    ) {
        this(slots, creatives, strategy, auctions, budgetService, freqCapService, hmacService,
                new LocalBudgetSliceService(budgetService), new CreativeInvertedIndex());
    }

    /**
     * 执行 OpenRTB 竞价核心主流程 (RTB Hot Path)
     *
     * @param request OpenRTB 2.5 竞价请求
     * @return 符合 OpenRTB 规范的竞价响应对象 (BidResponse)
     */
    public OpenRtb.BidResponse bid(OpenRtb.BidRequest request) {
        // 记录竞价开始的纳秒时间戳，用于严控 20ms 超时预算
        long startNanos = System.nanoTime();
        int tmax = (request.tmax() != null && request.tmax() > 0) ? request.tmax() : 20;

        // 计算硬截断死线：在第 15ms 强制截断后续多余计算，为 HTTP 响应序列化留足 5ms 缓冲
        long maxExecutionNanos = Math.min((long) tmax * 1_000_000L, 15_000_000L);
        long deadlineNanos = startNanos + maxExecutionNanos;

        String tenant = tenant();
        String userId = (request.user() != null && request.user().id() != null) ? request.user().id() : "anon";
        List<OpenRtb.Bid> bids = new ArrayList<>();

        // 懒加载：确保倒排索引在初次请求前已被装载入内存
        if (creativeIndex.totalActiveCount() == 0) {
            creativeIndex.loadAll(creatives.list());
        }

        // 遍历竞价请求中的每一个广告曝光机会 (Impression)
        for (OpenRtb.Imp impression : request.imp()) {
            // 阶段 1：检查是否达到硬超时截断死线，超限则立即中断循环，直接返回已有出价
            if (System.nanoTime() >= deadlineNanos) {
                break;
            }

            // 阶段 2：获取媒体端广告位配置，校验广告位是否活跃
            AdSlot slot;
            try {
                slot = slots.get(impression.id());
            } catch (RuntimeException ignored) {
                continue; // 未知或未配置广告位，跳过
            }
            if (!slot.active()) {
                continue; // 广告位已下线，不参与竞价
            }

            // 提取媒体请求的横幅尺寸（宽与高）
            int reqW = impression.banner() != null ? impression.banner().w() : 0;
            int reqH = impression.banner() != null ? impression.banner().h() : 0;

            // 阶段 3：从内存倒排索引中 O(1) 瞬时召回符合尺寸的可用候选素材列表
            List<Creative> candidates = creativeIndex.findCandidates(reqW, reqH);
            if (candidates.isEmpty()) {
                continue; // 无可用尺寸匹配的素材
            }

            // 阶段 4：执行竞价策略评分与底价加价计算
            strategy.choose(request, impression, slot, candidates).ifPresent(decision -> {
                String campaignId = "campaign_" + decision.advertiser();

                // 阶段 5：滑动窗口频控校验（例如限制单用户 1 小时最多 10 次曝光）
                boolean freqAllowed = freqCapService.checkAndIncrement(tenant, campaignId, userId, 10, Duration.ofHours(1));
                if (!freqAllowed) {
                    return; // 触发频控上限，放弃出价
                }

                // 阶段 6：微秒级本地预算切片原子预占（CPM 出价换算为单次曝光预占金额）
                BigDecimal impCost = BigDecimal.valueOf(decision.price() / 1000.0);
                BudgetService.Reservation reservation;
                try {
                    reservation = localBudgetService.reserveFast(tenant, campaignId, userId, impCost);
                } catch (IllegalStateException budgetExhausted) {
                    return; // 预算不足，放弃出价
                }

                // 阶段 7：生成加密防篡改的胜出通知 URL (Win Notice)
                String auctionId = auctions.nextId("auction");
                String winToken = hmacService.generateToken(auctionId, reservation.id(), tenant, campaignId, decision.price(), "WIN");
                String nurl = "/rtb/win?token=" + winToken;

                // 阶段 8：组装胜出出价对象
                String bidId = UUID.randomUUID().toString();
                bids.add(new OpenRtb.Bid(
                        bidId,
                        impression.id(),
                        decision.price(),
                        decision.creative().assetUrl(),
                        "example-advertiser.com",
                        nurl,
                        decision.creative().id()
                ));

                // 阶段 9：异步或内存落盘本次竞价出价记录
                auctions.save(new Auction(
                        auctionId,
                        request.id(),
                        slot.id(),
                        decision.creative().id(),
                        decision.price(),
                        "USD",
                        decision.advertiser(),
                        Instant.now()
                ));
            });
        }

        // 返回最终的 BidResponse（若无任何候选出价则返回空响应）
        return bids.isEmpty()
                ? new OpenRtb.BidResponse(request.id(), List.of(), "USD")
                : new OpenRtb.BidResponse(request.id(), List.of(new OpenRtb.SeatBid(bids, "default")), "USD");
    }

    /**
     * 获取素材内存倒排索引组件引用
     */
    public CreativeInvertedIndex getCreativeIndex() {
        return creativeIndex;
    }

    /**
     * 解析提取当前线程上下文中的租户标识
     */
    private static String tenant() {
        return TenantContext.get() == null ? "public" : TenantContext.get();
    }
}
