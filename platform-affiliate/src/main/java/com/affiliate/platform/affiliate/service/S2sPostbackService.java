package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.entity.AffiliatePartnerEntity;
import com.affiliate.platform.entity.ConversionEntity;
import com.affiliate.platform.mapper.AffiliatePartnerMapper;
import com.affiliate.platform.mapper.ConversionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 广告主 S2S 服务端转化归因与分发服务 (S2S Postback & Attribution Service - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL `affiliate_conversion` 与 `affiliate_partner` 表。
 */
@Service
public class S2sPostbackService {

    private final ClickTrackerService clickTracker;
    private final OfferService offerService;
    private final AffiliateAntiFraudEngine antiFraudEngine;
    private final PublisherPostbackDispatcher postbackDispatcher;

    private final ConversionMapper conversionMapper;
    private final AffiliatePartnerMapper partnerMapper;
    private final ProbabilisticAttributionEngine probabilisticEngine;

    private final ConcurrentMap<String, Conversion> fallbackConversions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AffiliatePartner> fallbackPartners = new ConcurrentHashMap<>();

    public S2sPostbackService(
            ClickTrackerService clickTracker,
            OfferService offerService,
            AffiliateAntiFraudEngine antiFraudEngine,
            PublisherPostbackDispatcher postbackDispatcher
    ) {
        this(clickTracker, offerService, antiFraudEngine, postbackDispatcher, null, null, null);
    }

    public S2sPostbackService(
            ClickTrackerService clickTracker,
            OfferService offerService,
            AffiliateAntiFraudEngine antiFraudEngine,
            PublisherPostbackDispatcher postbackDispatcher,
            ConversionMapper conversionMapper,
            AffiliatePartnerMapper partnerMapper
    ) {
        this(clickTracker, offerService, antiFraudEngine, postbackDispatcher, conversionMapper, partnerMapper, null);
    }

    @Autowired
    public S2sPostbackService(
            ClickTrackerService clickTracker,
            OfferService offerService,
            AffiliateAntiFraudEngine antiFraudEngine,
            PublisherPostbackDispatcher postbackDispatcher,
            @Autowired(required = false) ConversionMapper conversionMapper,
            @Autowired(required = false) AffiliatePartnerMapper partnerMapper,
            @Autowired(required = false) ProbabilisticAttributionEngine probabilisticEngine
    ) {
        this.clickTracker = clickTracker;
        this.offerService = offerService;
        this.antiFraudEngine = antiFraudEngine;
        this.postbackDispatcher = postbackDispatcher;
        this.conversionMapper = conversionMapper;
        this.partnerMapper = partnerMapper;
        this.probabilisticEngine = probabilisticEngine;
    }

    public void registerPartner(AffiliatePartner partner) {
        if (partnerMapper != null) {
            AffiliatePartnerEntity entity = new AffiliatePartnerEntity(
                    partner.id(),
                    partner.tenantId(),
                    partner.name(),
                    partner.status().name(),
                    partner.tier().name(),
                    partner.postbackUrlTemplate(),
                    partner.paymentTerm() != null ? partner.paymentTerm().name() : "NET_30",
                    partner.minPayoutThreshold(),
                    Instant.now(),
                    Instant.now()
            );
            if (partnerMapper.selectById(partner.id()) != null) {
                partnerMapper.updateById(entity);
            } else {
                partnerMapper.insert(entity);
            }
            return;
        }
        fallbackPartners.put(partner.id(), partner);
    }

    public Optional<AffiliatePartner> findPartner(String affiliateId) {
        if (partnerMapper != null) {
            AffiliatePartnerEntity entity = partnerMapper.selectById(affiliateId);
            return Optional.ofNullable(entity).map(this::toPartnerDomain);
        }
        return Optional.ofNullable(fallbackPartners.get(affiliateId));
    }

    public Conversion processPostback(String clickId, String txId, BigDecimal saleAmount, Instant now) {
        return processPostback(clickId, txId, saleAmount, null, null, null, null, 0, now);
    }

    /**
     * 增强版 S2S 服务端转化归因与上报处理
     *
     * @param clickId          点击会话唯一标识 (可为空，空时自动触发概率性指纹兜底)
     * @param txId             广告主订单唯一流水号 (必填，用于幂等去重)
     * @param saleAmount       订单销售金额 (用于 CPS 按比例分成)
     * @param goalId           多事件漏斗目标标识 (可选，如 INSTALL / REGISTRATION / PURCHASE)
     * @param offerIdFallback  当 clickId 丢失时的兜底 Offer 标识
     * @param ip               转化发生地客户端 IP
     * @param userAgent        转化发生地客户端 User-Agent
     * @param deviceType       设备类型 (1=Mobile, 2=Desktop, etc.)
     * @param now              转化上报时间
     * @return 归因对齐与风控核验后的最终转化记录
     */
    public Conversion processPostback(
            String clickId,
            String txId,
            BigDecimal saleAmount,
            String goalId,
            String offerIdFallback,
            String ip,
            String userAgent,
            int deviceType,
            Instant now
    ) {
        if (txId == null || txId.isBlank()) {
            throw new IllegalArgumentException("txId must not be blank");
        }

        Instant current = now == null ? Instant.now() : now;

        // 1. 提取点击会话存根 (支持精准 click_id 检索与概率性设备指纹兜底)
        ClickSession session = null;
        if (clickId != null && !clickId.isBlank()) {
            session = clickTracker.findSession(clickId);
        }

        // 若 click_id 缺失或未命中，且启用了概率性指纹引擎，则尝试基于 IP + UA 模糊匹配
        if (session == null && probabilisticEngine != null && offerIdFallback != null && ip != null) {
            ProbabilisticAttributionEngine.ProbabilisticMatchResult match =
                    probabilisticEngine.matchAttribution(offerIdFallback, ip, userAgent, "US", deviceType);
            if (match.matched()) {
                session = match.matchedSession();
            }
        }

        if (session == null) {
            String convId = "conv_" + UUID.randomUUID().toString().replace("-", "");
            Conversion rejected = new Conversion(convId, "default", clickId != null ? clickId : "unmatched", txId,
                    offerIdFallback != null ? offerIdFallback : "unknown", "unknown",
                    BigDecimal.ZERO, BigDecimal.ZERO, saleAmount, 0,
                    Conversion.Status.REJECTED, "CLICK_SESSION_NOT_FOUND", current);
            saveConversion(rejected);
            return rejected;
        }

        // 2. 反欺诈与 CTIT 质检 (使用商业级多维评分)
        AffiliateAntiFraudEngine.FraudInspectionResult fraudRes = antiFraudEngine.inspectConversion(session, txId, current);
        long ctit = Duration.between(session.createdAt(), current).toSeconds();

        // 3. 读取 Offer 与渠道出价 (优先匹配多事件 OfferGoal)
        Offer offer = offerService.find(session.offerId()).orElse(null);
        AffiliatePartner partner = findPartner(session.affiliateId()).orElse(null);

        BigDecimal payout = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;

        if (offer != null) {
            // 3.1 优先检查是否存在匹配的多事件目标 (OfferGoal)
            com.affiliate.platform.affiliate.domain.OfferGoal matchedGoal = null;
            if (goalId != null && !goalId.isBlank()) {
                matchedGoal = offerService.findGoal(offer.id(), goalId).orElse(null);
            }

            if (matchedGoal != null) {
                payout = matchedGoal.payout();
                revenue = matchedGoal.revenue();
            } else if (offer.payoutType() == Offer.PayoutType.CPS && saleAmount != null && saleAmount.signum() > 0) {
                payout = saleAmount.multiply(offer.defaultPayout());
                revenue = saleAmount.multiply(offer.defaultRevenue());
            } else {
                OfferService.PayoutResolution resolution = offerService.resolvePayout(offer, partner);
                payout = resolution.payout();
                revenue = resolution.revenue();
            }
        }

        // 4. 检查日 Cap
        String todayKey = LocalDate.now().toString();
        boolean capAvailable = offerService.incrementAndCheckCap(session.offerId(), todayKey);
        if (!capAvailable && fraudRes.passed()) {
            fraudRes = new AffiliateAntiFraudEngine.FraudInspectionResult(false, Conversion.Status.REJECTED, "OFFER_CAP_EXCEEDED");
        }

        // 5. 生成转化实体
        String convId = "conv_" + UUID.randomUUID().toString().replace("-", "");
        Conversion conversion = new Conversion(
                convId,
                session.tenantId(),
                session.clickId(),
                txId,
                session.offerId(),
                session.affiliateId(),
                payout,
                revenue,
                saleAmount,
                ctit,
                fraudRes.recommendedStatus(),
                fraudRes.rejectionReason(),
                current
        );

        saveConversion(conversion);

        // 6. 若风控通过，分发下游 Postback
        if (fraudRes.passed() && partner != null) {
            postbackDispatcher.dispatch(partner, conversion, session);
        }

        return conversion;
    }

    private void saveConversion(Conversion conv) {
        if (conversionMapper != null) {
            ConversionEntity entity = new ConversionEntity(
                    conv.id(),
                    conv.tenantId(),
                    conv.clickId(),
                    conv.offerId(),
                    conv.affiliateId(),
                    conv.txId(),
                    conv.payout(),
                    conv.revenue(),
                    conv.status().name(),
                    conv.ctitSeconds(),
                    null,
                    "DELIVERED",
                    conv.createdAt()
            );
            if (conversionMapper.selectById(conv.id()) != null) {
                conversionMapper.updateById(entity);
            } else {
                conversionMapper.insert(entity);
            }
            return;
        }
        fallbackConversions.put(conv.id(), conv);
    }

    public Optional<Conversion> findConversion(String conversionId) {
        if (conversionMapper != null) {
            ConversionEntity entity = conversionMapper.selectById(conversionId);
            return Optional.ofNullable(entity).map(this::toConversionDomain);
        }
        return Optional.ofNullable(fallbackConversions.get(conversionId));
    }

    public void updateConversion(Conversion conversion) {
        if (conversion != null && conversion.id() != null) {
            saveConversion(conversion);
        }
    }

    public List<Conversion> listConversions() {
        if (conversionMapper != null) {
            QueryWrapper<ConversionEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("created_at").last("LIMIT 1000");
            List<ConversionEntity> list = conversionMapper.selectList(qw);
            return list.stream().map(this::toConversionDomain).toList();
        }
        return List.copyOf(fallbackConversions.values());
    }

    public List<AffiliatePartner> listPartners() {
        if (partnerMapper != null) {
            QueryWrapper<AffiliatePartnerEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("created_at").last("LIMIT 1000");
            List<AffiliatePartnerEntity> list = partnerMapper.selectList(qw);
            return list.stream().map(this::toPartnerDomain).toList();
        }
        return List.copyOf(fallbackPartners.values());
    }

    private Conversion toConversionDomain(ConversionEntity e) {
        return new Conversion(
                e.getId(),
                e.getTenantId(),
                e.getClickId(),
                e.getTransactionId(),
                e.getOfferId(),
                e.getAffiliateId(),
                e.getPayout(),
                e.getRevenue(),
                BigDecimal.ZERO,
                e.getCtitSeconds() != null ? e.getCtitSeconds() : 0,
                Conversion.Status.valueOf(e.getStatus()),
                null,
                e.getCreatedAt()
        );
    }

    private AffiliatePartner toPartnerDomain(AffiliatePartnerEntity e) {
        AffiliatePartner.PaymentTerm term = AffiliatePartner.PaymentTerm.NET_30;
        if (e.getPaymentTerm() != null) {
            try {
                term = AffiliatePartner.PaymentTerm.valueOf(e.getPaymentTerm());
            } catch (Exception ignored) {}
        }
        return new AffiliatePartner(
                e.getId(),
                e.getTenantId(),
                e.getName(),
                e.getStatus() != null ? AffiliatePartner.Status.valueOf(e.getStatus()) : AffiliatePartner.Status.ACTIVE,
                e.getTier() != null ? AffiliatePartner.Tier.valueOf(e.getTier()) : AffiliatePartner.Tier.STANDARD,
                e.getPostbackUrlTemplate(),
                term,
                e.getMinPayoutThreshold(),
                e.getCreatedAt()
        );
    }
}
