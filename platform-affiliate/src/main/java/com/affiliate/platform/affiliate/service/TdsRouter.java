package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.SmartLink;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public TdsRouter(OfferService offerService) {
        this.offerService = offerService;
    }

    public void updateOfferEpc(String offerId, BigDecimal epc) {
        if (offerId != null && epc != null) {
            offerEpcCache.put(offerId, epc);
        }
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
            Offer offer = offerService.find(offerId).orElse(null);
            if (offer != null && offer.matches(country, deviceType) && !offerService.isCapReached(offer.id(), dateKey)) {
                candidates.add(offer);
            }
        }

        if (candidates.isEmpty()) {
            // 无直接候选命中，尝试回退至 SmartLink 保底 Offer
            if (smartLink.fallbackOfferId() != null) {
                return offerService.resolveActiveOfferWithFallback(smartLink.fallbackOfferId(), dateKey);
            }
            return null;
        }

        // 若仅有 1 个候选，直接返回
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 多候选时依据路由策略优选 (HIGHEST_EPC)
        if (smartLink.routingStrategy() == SmartLink.RoutingStrategy.HIGHEST_EPC) {
            candidates.sort((o1, o2) -> {
                BigDecimal epc1 = offerEpcCache.getOrDefault(o1.id(), BigDecimal.ZERO);
                BigDecimal epc2 = offerEpcCache.getOrDefault(o2.id(), BigDecimal.ZERO);
                return epc2.compareTo(epc1); // 降序排列，EPC 最高的排在首位
            });
            return candidates.get(0);
        }

        // 默认返回首个
        return candidates.get(0);
    }
}
