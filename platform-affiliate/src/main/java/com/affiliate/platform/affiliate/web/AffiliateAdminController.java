package com.affiliate.platform.affiliate.web;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Conversion;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.SmartLink;
import com.affiliate.platform.affiliate.service.*;
import com.affiliate.platform.entity.SmartLinkEntity;
import com.affiliate.platform.mapper.SmartLinkMapper;
import com.affiliate.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 网盟管理控制台 REST API (Affiliate Network Admin REST Controller - MyBatis-Plus)
 * <p>
 * 提供给前端管理界面（Vue 3 + Nuxt 4 + Vite）调用的完整业务接口：
 * 包含监控大盘概况、Offer 计划管理、SmartLink TDS 路由管理、渠道客入驻、
 * 转化归因人工审批流、财务发票出账与 Sub-ID 报表多维下钻。
 */
@RestController
@RequestMapping("/api/v1/affiliate")
public class AffiliateAdminController {

    private final OfferService offerService;
    private final S2sPostbackService postbackService;
    private final AffiliateSettlementService settlementService;
    private final SubIdAnalyticsService analyticsService;
    private final TdsRouter tdsRouter;
    private final SmartLinkMapper smartLinkMapper;
    private final AffiliateAntiFraudEngine antiFraudEngine;

    // SmartLink 内存降级注册表
    private final ConcurrentMap<String, SmartLink> smartLinks = new ConcurrentHashMap<>();

    public AffiliateAdminController(
            OfferService offerService,
            S2sPostbackService postbackService,
            AffiliateSettlementService settlementService,
            SubIdAnalyticsService analyticsService,
            TdsRouter tdsRouter
    ) {
        this(offerService, postbackService, settlementService, analyticsService, tdsRouter, null, null);
    }

    public AffiliateAdminController(
            OfferService offerService,
            S2sPostbackService postbackService,
            AffiliateSettlementService settlementService,
            SubIdAnalyticsService analyticsService,
            TdsRouter tdsRouter,
            SmartLinkMapper smartLinkMapper
    ) {
        this(offerService, postbackService, settlementService, analyticsService, tdsRouter, smartLinkMapper, null);
    }

    @Autowired
    public AffiliateAdminController(
            OfferService offerService,
            S2sPostbackService postbackService,
            AffiliateSettlementService settlementService,
            SubIdAnalyticsService analyticsService,
            TdsRouter tdsRouter,
            @Autowired(required = false) SmartLinkMapper smartLinkMapper,
            @Autowired(required = false) AffiliateAntiFraudEngine antiFraudEngine
    ) {
        this.offerService = offerService;
        this.postbackService = postbackService;
        this.settlementService = settlementService;
        this.analyticsService = analyticsService;
        this.tdsRouter = tdsRouter;
        this.smartLinkMapper = smartLinkMapper;
        this.antiFraudEngine = antiFraudEngine != null ? antiFraudEngine : new AffiliateAntiFraudEngine();
    }

    // ==========================================
    // 1. 监控大盘总览 (Dashboard Overview)
    // ==========================================
    /** 当前请求线程租户；无上下文（离线测试）时不收窄 */
    private static Optional<String> requestTenant() {
        String tenant = TenantContext.get();
        return tenant != null && !tenant.isBlank() ? Optional.of(tenant) : Optional.empty();
    }

    /** 判断实体租户归属：无请求上下文或实体缺失租户时放行 */
    private static boolean withinTenant(String entityTenantId) {
        Optional<String> tenant = requestTenant();
        return tenant.isEmpty() || entityTenantId == null || entityTenantId.isBlank() || tenant.get().equals(entityTenantId);
    }

    @GetMapping("/dashboard/overview")
    public Map<String, Object> getOverview() {
        List<Offer> offers = offerService.list().stream().filter(o -> withinTenant(o.tenantId())).toList();
        List<AffiliatePartner> partners = postbackService.listPartners().stream()
                .filter(p -> withinTenant(p.tenantId())).toList();
        List<Conversion> conversions = postbackService.listConversions().stream()
                .filter(c -> withinTenant(c.tenantId())).toList();

        long totalConversions = conversions.size();
        long approvedConversions = conversions.stream().filter(c -> c.status() == Conversion.Status.APPROVED).count();
        // 仅 APPROVED 转化进入营收/佣金/毛利等可结算金额口径
        List<Conversion> approvedList = conversions.stream().filter(c -> c.status() == Conversion.Status.APPROVED).toList();
        BigDecimal totalPayout = approvedList.stream().map(Conversion::payout).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevenue = approvedList.stream().map(Conversion::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossProfit = totalRevenue.subtract(totalPayout);

        // 近 7 天 vs 前 7 天环比趋势
        Instant now = Instant.now();
        Instant weekStart = now.minus(java.time.Duration.ofDays(7));
        Instant twoWeeksAgo = now.minus(java.time.Duration.ofDays(14));
        List<Conversion> curWeek = conversions.stream().filter(c -> c.createdAt() != null && c.createdAt().isAfter(weekStart)).toList();
        List<Conversion> prevWeek = conversions.stream()
                .filter(c -> c.createdAt() != null && c.createdAt().isAfter(twoWeeksAgo) && !c.createdAt().isAfter(weekStart)).toList();

        // 仅 APPROVED 转化进入可结算金额（CONTEXT.md 状态机约束）
        BigDecimal curRevenue = curWeek.stream().filter(c -> c.status() == Conversion.Status.APPROVED)
                .map(Conversion::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prevRevenue = prevWeek.stream().filter(c -> c.status() == Conversion.Status.APPROVED)
                .map(Conversion::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal curProfit = curRevenue.subtract(curWeek.stream().filter(c -> c.status() == Conversion.Status.APPROVED)
                .map(Conversion::payout).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal prevProfit = prevRevenue.subtract(prevWeek.stream().filter(c -> c.status() == Conversion.Status.APPROVED)
                .map(Conversion::payout).reduce(BigDecimal.ZERO, BigDecimal::add));

        long curPartners = partners.stream().filter(p -> p.createdAt() != null && p.createdAt().isAfter(weekStart)).count();
        long prevPartners = partners.stream()
                .filter(p -> p.createdAt() != null && p.createdAt().isAfter(twoWeeksAgo) && !p.createdAt().isAfter(weekStart)).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOffers", offers.size());
        result.put("activeOffers", offers.stream().filter(Offer::isAvailable).count());
        result.put("totalPartners", partners.size());
        result.put("totalConversions", totalConversions);
        result.put("approvedConversions", approvedConversions);
        result.put("totalPayout", totalPayout);
        result.put("totalRevenue", totalRevenue);
        result.put("grossProfit", grossProfit);
        result.put("revenueTrendPercent", trendPercent(prevRevenue, curRevenue));
        result.put("profitTrendPercent", trendPercent(prevProfit, curProfit));
        result.put("conversionTrendPercent", trendPercent(
                BigDecimal.valueOf(prevWeek.stream().filter(c -> c.status() == Conversion.Status.APPROVED).count()),
                BigDecimal.valueOf(curWeek.stream().filter(c -> c.status() == Conversion.Status.APPROVED).count())));
        result.put("partnerTrendPercent", trendPercent(BigDecimal.valueOf(prevPartners), BigDecimal.valueOf(curPartners)));
        return result;
    }

    private static double trendPercent(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // ==========================================
    // 2. Offer 推广计划管理 (Offer Management)
    // ==========================================
    @GetMapping("/offers")
    public List<Offer> listOffers() {
        return offerService.list();
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public Offer createOffer(@Valid @RequestBody Offer offer) {
        if (offer.id() == null || offer.id().isBlank()) {
            offer = offer.withId("off_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        return offerService.save(offer);
    }

    @GetMapping("/offers/{id}")
    public ResponseEntity<Offer> getOffer(@PathVariable String id) {
        return offerService.find(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==========================================
    // 3. SmartLink / TDS 智能分流管理
    // ==========================================
    @GetMapping("/smartlinks")
    public List<SmartLink> listSmartLinks() {
        if (smartLinkMapper != null) {
            List<SmartLinkEntity> entities = smartLinkMapper.selectList(null);
            if (entities != null && !entities.isEmpty()) {
                return entities.stream().map(this::toSmartLinkDomain).toList();
            }
        }
        return List.copyOf(smartLinks.values());
    }

    @PostMapping("/smartlinks")
    @ResponseStatus(HttpStatus.CREATED)
    public SmartLink saveSmartLink(@Valid @RequestBody SmartLink link) {
        if (link.id() == null || link.id().isBlank()) {
            link = new SmartLink(
                    "sl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    link.tenantId(), link.name(), link.category(), link.targetOfferIds(),
                    link.routingStrategy(), link.fallbackOfferId(), link.createdAt()
            );
        }
        smartLinks.put(link.id(), link);
        if (smartLinkMapper != null) {
            SmartLinkEntity entity = new SmartLinkEntity(
                    link.id(),
                    link.tenantId() != null ? link.tenantId() : "public",
                    link.name(),
                    link.category(),
                    link.targetOfferIds(),
                    link.routingStrategy() != null ? link.routingStrategy().name() : "HIGHEST_EPC",
                    link.fallbackOfferId(),
                    link.createdAt()
            );
            if (smartLinkMapper.selectById(link.id()) != null) {
                smartLinkMapper.updateById(entity);
            } else {
                smartLinkMapper.insert(entity);
            }
        }
        return link;
    }

    @GetMapping("/smartlinks/{id}/simulate")
    public ResponseEntity<Offer> simulateRouting(
            @PathVariable String id,
            @RequestParam(defaultValue = "US") String country,
            @RequestParam(defaultValue = "1") int deviceType
    ) {
        SmartLink link = null;
        if (smartLinkMapper != null) {
            SmartLinkEntity entity = smartLinkMapper.selectById(id);
            if (entity != null) {
                link = toSmartLinkDomain(entity);
            }
        }
        if (link == null) {
            link = smartLinks.get(id);
        }
        if (link == null) {
            return ResponseEntity.notFound().build();
        }
        Offer routed = tdsRouter.route(link, country, deviceType, "today");
        return routed != null ? ResponseEntity.ok(routed) : ResponseEntity.noContent().build();
    }

    // ==========================================
    // 4. 渠道客管理 (Affiliate Partner Management)
    // ==========================================
    @GetMapping("/partners")
    public List<AffiliatePartner> listPartners() {
        return postbackService.listPartners();
    }

    @PostMapping("/partners")
    @ResponseStatus(HttpStatus.CREATED)
    public AffiliatePartner savePartner(@Valid @RequestBody AffiliatePartner partner) {
        if (partner.id() == null || partner.id().isBlank()) {
            partner = new AffiliatePartner(
                    "aff_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    partner.tenantId(), partner.name(), partner.status(), partner.tier(),
                    partner.postbackUrlTemplate(), partner.paymentTerm(),
                    partner.minPayoutThreshold(), partner.createdAt()
            );
        }
        postbackService.registerPartner(partner);
        return partner;
    }

    // ==========================================
    // 5. 转化归因与人工审核 (Conversions & Approval)
    // ==========================================
    @GetMapping("/conversions")
    public List<Conversion> listConversions() {
        return postbackService.listConversions();
    }

    @PostMapping("/conversions/{id}/approve")
    public Conversion approveConversion(@PathVariable String id) {
        return settlementService.approve(id);
    }

    @PostMapping("/conversions/{id}/reject")
    public Conversion rejectConversion(@PathVariable String id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "MANUAL_REJECTED");
        return settlementService.reject(id, reason);
    }

    // ==========================================
    // 6. 财务出账与结算账单 (Settlement & Invoices)
    // ==========================================
    @GetMapping("/invoices")
    public List<AffiliateSettlementService.AffiliateInvoice> listInvoices() {
        return settlementService.listInvoices();
    }

    @PostMapping("/invoices/generate")
    public ResponseEntity<AffiliateSettlementService.AffiliateInvoice> generateInvoice(
            @RequestParam String affiliateId
    ) {
        AffiliatePartner partner = postbackService.findPartner(affiliateId).orElse(null);
        if (partner == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<AffiliateSettlementService.AffiliateInvoice> invoice = settlementService.generateInvoice(affiliateId, partner);
        return invoice.map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/invoices/{id}/mark-paid")
    public ResponseEntity<AffiliateSettlementService.AffiliateInvoice> markInvoicePaid(@PathVariable String id) {
        return settlementService.markInvoicePaid(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==========================================
    // 7. Sub-ID 报表分析 (Sub-ID Analytics)
    // ==========================================
    @GetMapping("/analytics/subid")
    public SubIdAnalyticsService.SubIdPerformance getSubIdAnalytics(
            @RequestParam(required = false, defaultValue = "all") String affiliateId,
            @RequestParam(required = false, defaultValue = "default") String sub1
    ) {
        return analyticsService.getPerformance(affiliateId, sub1);
    }

    @GetMapping("/analytics/subid/list")
    public List<SubIdAnalyticsService.SubIdPerformance> listSubIdAnalytics(
            @RequestParam(required = false) String affiliateId,
            @RequestParam(required = false) String sub1
    ) {
        return analyticsService.listPerformance(affiliateId, sub1);
    }

    // ==========================================
    // 8. 反欺诈与风控中控台 (Anti-Fraud Console)
    // ==========================================
    @GetMapping("/antifraud/stats")
    public Map<String, Object> getAntiFraudStats() {
        Map<String, Long> counters = antiFraudEngine.getCumulativeCounters();
        List<Conversion> conversions = postbackService.listConversions().stream()
                .filter(c -> withinTenant(c.tenantId())).toList();
        List<Conversion> blocked = conversions.stream()
                .filter(c -> c.status() == Conversion.Status.FRAUD_SUSPECTED || c.status() == Conversion.Status.REJECTED)
                .toList();
        BigDecimal savedAmount = blocked.stream().map(Conversion::payout).reduce(BigDecimal.ZERO, BigDecimal::add);
        double interceptRate = conversions.isEmpty() ? 0.0
                : BigDecimal.valueOf(blocked.size() * 100.0 / conversions.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("interceptRatePercent", interceptRate);
        summary.put("savedAmountUsd", savedAmount);
        summary.put("blockedConversions", blocked.size());
        summary.put("blacklistCount", antiFraudEngine.getIpBlacklist().size() + antiFraudEngine.getSubIdBlacklist().size());
        summary.putAll(counters);

        return Map.of(
                "recentLogs", antiFraudEngine.getRecentRiskLogs(),
                "ipBlacklist", antiFraudEngine.getIpBlacklist(),
                "subIdBlacklist", antiFraudEngine.getSubIdBlacklist(),
                "summary", summary
        );
    }

    @PostMapping("/antifraud/blacklist/ip")
    public ResponseEntity<Void> addIpBlacklist(@RequestParam String ip) {
        antiFraudEngine.addIpToBlacklist(ip);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/antifraud/blacklist/ip")
    public ResponseEntity<Void> removeIpBlacklist(@RequestParam String ip) {
        antiFraudEngine.removeIpFromBlacklist(ip);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/antifraud/blacklist/subid")
    public ResponseEntity<Void> addSubIdBlacklist(@RequestParam String subId) {
        antiFraudEngine.addSubIdToBlacklist(subId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/antifraud/blacklist/subid")
    public ResponseEntity<Void> removeSubIdBlacklist(@RequestParam String subId) {
        antiFraudEngine.removeSubIdFromBlacklist(subId);
        return ResponseEntity.ok().build();
    }

    // ==========================================
    // 9. 多事件转化目标管理 (Offer Goals)
    // ==========================================
    @GetMapping("/offers/{id}/goals")
    public List<com.affiliate.platform.affiliate.domain.OfferGoal> listGoals(@PathVariable String id) {
        return offerService.listGoals(id);
    }

    @PostMapping("/offers/{id}/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public com.affiliate.platform.affiliate.domain.OfferGoal createGoal(
            @PathVariable String id,
            @Valid @RequestBody com.affiliate.platform.affiliate.domain.OfferGoal goal
    ) {
        offerService.addGoal(goal);
        return goal;
    }

    @DeleteMapping("/offers/{id}/goals/{goalId}")
    public ResponseEntity<Void> deleteGoal(@PathVariable String id, @PathVariable String goalId) {
        boolean removed = offerService.removeGoal(id, goalId);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private SmartLink toSmartLinkDomain(SmartLinkEntity entity) {
        return new SmartLink(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getCategory(),
                entity.getTargetOfferIds() != null ? entity.getTargetOfferIds() : List.of(),
                entity.getRoutingStrategy() != null ? SmartLink.RoutingStrategy.valueOf(entity.getRoutingStrategy()) : SmartLink.RoutingStrategy.HIGHEST_EPC,
                entity.getFallbackOfferId(),
                entity.getCreatedAt()
        );
    }
}
