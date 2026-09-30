package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.SmartLink;
import com.affiliate.platform.geo.IpLocationResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 流量分发与智能分流路由引擎 (TDS - Traffic Distribution System)
 * <p>
 * 解决 SmartLink 场景的核心诉求：
 * 根据访客的地理位置（Country）、终端形态（DeviceType）与各候选 Offer 实时统计的
 * 收益转化表现（EPC - Earnings Per Click），动态自适应路由至产出最高的可用推广计划。
 */
@Service
public class TdsRouter {

    private final OfferService offerService;

    // 缓存各 Offer 的最新平均 EPC 分值（供动态排序比价）
    private final ConcurrentMap<String, BigDecimal> offerEpcCache = new ConcurrentHashMap<>();

    // 记录智能分流链接维度的轮询计数器 (Round-Robin Atomic Counters)
    private final ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    // 记录被临时熔断/降级的 Offer 及其恢复时间戳 (Degraded Offers Circuit Breaker)
    private final ConcurrentMap<String, Instant> degradedOffers = new ConcurrentHashMap<>();
    private final com.affiliate.platform.affiliate.metrics.AffiliateMetrics metrics;

    public TdsRouter(OfferService offerService) {
        this(offerService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TdsRouter(
            OfferService offerService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.affiliate.platform.affiliate.metrics.AffiliateMetrics metrics
    ) {
        this.offerService = offerService;
        this.metrics = metrics != null ? metrics : new com.affiliate.platform.affiliate.metrics.AffiliateMetrics();
    }

    public void updateOfferEpc(String offerId, BigDecimal epc) {
        if (offerId != null && epc != null) {
            offerEpcCache.put(offerId, epc);
        }
    }

    public BigDecimal getOfferEpc(String offerId) {
        return offerId != null ? offerEpcCache.getOrDefault(offerId, BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    /**
     * 临时熔断或降级指定 Offer（在指定时长内不再参与流量分发）
     *
     * @param offerId  推广活动 ID
     * @param duration 熔断时长
     */
    public void markOfferDegraded(String offerId, java.time.Duration duration) {
        if (offerId != null && duration != null) {
            degradedOffers.put(offerId, Instant.now().plus(duration));
            metrics.updateDegradedOfferCount(degradedOffers.size());
        }
    }

    public boolean isOfferDegraded(String offerId) {
        if (offerId == null) {
            return false;
        }
        Instant expireAt = degradedOffers.get(offerId);
        if (expireAt == null) {
            return false;
        }
        if (Instant.now().isAfter(expireAt)) {
            degradedOffers.remove(offerId);
            metrics.updateDegradedOfferCount(degradedOffers.size());
            return false;
        }
        return true;
    }

    /**
     * 重置轮询计数器与状态缓存
     */
    public void resetRoutingState() {
        roundRobinCounters.clear();
        degradedOffers.clear();
    }

    /**
     * 执行 SmartLink 智能路由决断 (结合 Client IP 自动解析物理国家)
     *
     * @param smartLink  智能分流链接配置
     * @param clientIp   访客客户端 IP
     * @param country    访客显式传入国家代码 (为空或默认时从 IP 自动解析)
     * @param deviceType 访客设备类型
     * @param dateKey    当前日期标识
     * @return 最终选取的最佳有效 Offer
     */
    public Offer route(SmartLink smartLink, String clientIp, String country, int deviceType, String dateKey) {
        String effectiveCountry = country;
        if ((effectiveCountry == null || effectiveCountry.isBlank() || "US".equalsIgnoreCase(effectiveCountry))
                && clientIp != null && !clientIp.isBlank()) {
            String detected = IpLocationResolver.getCountryCode(clientIp);
            if (detected != null && !detected.isBlank() && !"ZZ".equalsIgnoreCase(detected)) {
                effectiveCountry = detected;
            }
        }
        return route(smartLink, effectiveCountry, deviceType, dateKey);
    }

    /**
     * 执行 SmartLink 智能路由决断
     *
     * @param smartLink  智能分流链接配置
     * @param country    访客国家代码
     * @param deviceType 访客设备类型
     * @param dateKey    当前日期标识
     * @return 最终选取的最佳有效 Offer（未命中或超限则退至 fallbackOffer）
     */
    public Offer route(SmartLink smartLink, String country, int deviceType, String dateKey) {
        if (smartLink == null || smartLink.targetOfferIds().isEmpty()) {
            return null;
        }

        List<Offer> candidates = new ArrayList<>();
        for (String offerId : smartLink.targetOfferIds()) {
            if (isOfferDegraded(offerId)) {
                continue;
            }
            Offer offer = offerService.find(offerId).orElse(null);
            if (offer != null && offer.matches(country, deviceType) && !offerService.isCapReached(offer.id(), dateKey)) {
                candidates.add(offer);
            }
        }

        if (candidates.isEmpty()) {
            // 无直接候选命中，尝试回退至 SmartLink 保底 Offer
            if (smartLink.fallbackOfferId() != null) {
                metrics.recordTdsRouteFallback();
                return offerService.resolveActiveOfferWithFallback(smartLink.fallbackOfferId(), dateKey);
            }
            return null;
        }

        // 若仅有 1 个候选，直接返回
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        String linkKey = smartLink.id() != null ? smartLink.id() : "default";

        // 1. 均摊轮询策略 (ROUND_ROBIN)
        if (smartLink.routingStrategy() == SmartLink.RoutingStrategy.ROUND_ROBIN) {
            metrics.recordTdsRouteRoundRobin();
            java.util.concurrent.atomic.AtomicInteger counter = roundRobinCounters.computeIfAbsent(
                    linkKey + ":rr", k -> new java.util.concurrent.atomic.AtomicInteger(0)
            );
            int index = Math.floorMod(counter.getAndIncrement(), candidates.size());
            return candidates.get(index);
        }

        // 2. 收益最大化策略 (HIGHEST_EPC) - 带平局均摊防饿死机制
        if (smartLink.routingStrategy() == SmartLink.RoutingStrategy.HIGHEST_EPC) {
            metrics.recordTdsRouteHighestEpc();
            BigDecimal maxEpc = null;
            List<Offer> topOffers = new ArrayList<>();

            for (Offer candidate : candidates) {
                BigDecimal epc = offerEpcCache.getOrDefault(candidate.id(), BigDecimal.ZERO);
                if (maxEpc == null || epc.compareTo(maxEpc) > 0) {
                    maxEpc = epc;
                    topOffers.clear();
                    topOffers.add(candidate);
                } else if (epc.compareTo(maxEpc) == 0) {
                    topOffers.add(candidate);
                }
            }

            if (topOffers.size() == 1) {
                return topOffers.get(0);
            }

            // 存在相同最高 EPC 的多个候选 Offer 时，平滑轮询分配，防止靠前 Offer 长期饥饿垄断
            java.util.concurrent.atomic.AtomicInteger tieCounter = roundRobinCounters.computeIfAbsent(
                    linkKey + ":tie", k -> new java.util.concurrent.atomic.AtomicInteger(0)
            );
            int index = Math.floorMod(tieCounter.getAndIncrement(), topOffers.size());
            return topOffers.get(index);
        }

        // 默认返回首个
        return candidates.get(0);
    }
}
