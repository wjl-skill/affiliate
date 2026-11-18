package com.affiliate.platform.rtb;

import com.affiliate.platform.cdp.RealtimeEventTraitEngine;
import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.dsp.*;
import com.affiliate.platform.ssp.DynamicYieldManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * RTB 全栈智能竞价协同桥接器 (Unified SSP + CDP + DSP Smart Bidding Pipeline Bridge)
 * <p>
 * 将原先分散在 SSP、CDP、DSP 的核心算法串联进 OpenRTB 2.5 亚毫秒竞价热路径：
 * <ol>
 *     <li><b>SSP 自适应底价寻优</b>：调用 {@link DynamicYieldManager#resolveAdaptiveFloor} 动态调整广告位软硬底价；</li>
 *     <li><b>CDP 亚毫秒画像增强</b>：调用 {@link RealtimeEventTraitEngine#getSnapshot} 提取用户高净值标签（如 {@code whale_purchaser}）进行溢价；</li>
 *     <li><b>DSP 深度 FM 推理与 Bid Shading</b>：调用 {@link DeepCtrCvrInferenceEngine} 预估 $\text{pCTR} \times \text{pCVR}$ 并通过 {@link DynamicBidShadingEngine} 折算最优一价出价。</li>
 * </ol>
 */
@Component
public class RtbSmartBiddingBridge {

    private final DynamicYieldManager yieldManager;
    private final RealtimeEventTraitEngine traitEngine;
    private final DeepCtrCvrInferenceEngine ctrCvrEngine;
    private final DynamicBidShadingEngine bidShadingEngine;
    @Autowired(required = false)
    private CampaignService campaignService;
    @Autowired(required = false)
    private CampaignInvertedIndex campaignInvertedIndex;
    @Autowired(required = false)
    private BidModifierEngine bidModifierEngine;
    @Autowired(required = false)
    private BudgetPacingController budgetPacingController;

    private final com.google.common.cache.Cache<String, java.util.concurrent.atomic.AtomicLong> intradaySpendMicrosMap =
            com.google.common.cache.CacheBuilder.newBuilder()
                    .maximumSize(50_000)
                    .expireAfterWrite(java.time.Duration.ofHours(36))
                    .build();
    private final java.util.concurrent.ConcurrentMap<String, BigDecimal> dailyBudgetOverrides =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Double pacingElapsedFractionOverride;

    public record PacingCheckResult(boolean throttled, double bidMultiplier) {}

    private final AtomicLong totalCalibrations = new AtomicLong(0);
    private final AtomicLong cdpBoostedBids = new AtomicLong(0);
    private final AtomicLong dspIndexRecalledBids = new AtomicLong(0);
    private final AtomicLong pacingThrottledBids = new AtomicLong(0);
    private final DoubleAdder totalShadedSavings = new DoubleAdder();

    @Autowired
    public RtbSmartBiddingBridge(
            @Autowired(required = false) DynamicYieldManager yieldManager,
            @Autowired(required = false) RealtimeEventTraitEngine traitEngine,
            @Autowired(required = false) DeepCtrCvrInferenceEngine ctrCvrEngine,
            @Autowired(required = false) DynamicBidShadingEngine bidShadingEngine
    ) {
        this.yieldManager = yieldManager;
        this.traitEngine = traitEngine;
        this.ctrCvrEngine = ctrCvrEngine;
        this.bidShadingEngine = bidShadingEngine;
    }

    public void setCampaignService(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    public void setCampaignInvertedIndex(CampaignInvertedIndex campaignInvertedIndex) {
        this.campaignInvertedIndex = campaignInvertedIndex;
    }

    public void setBidModifierEngine(BidModifierEngine bidModifierEngine) {
        this.bidModifierEngine = bidModifierEngine;
    }

    public void setBudgetPacingController(BudgetPacingController budgetPacingController) {
        this.budgetPacingController = budgetPacingController;
    }

    public void setPacingElapsedFractionOverride(Double elapsedDayFraction) {
        this.pacingElapsedFractionOverride = elapsedDayFraction;
    }

    public void setCampaignDailyBudget(String campaignId, BigDecimal dailyBudget) {
        if (campaignId != null && dailyBudget != null) {
            dailyBudgetOverrides.put(campaignId, dailyBudget);
        }
    }

    private static String spendKey(String campaignId) {
        return campaignId + ":" + LocalDate.now(ZoneOffset.UTC);
    }

    public void recordCampaignSpend(String campaignId, double costUsd) {
        if (campaignId != null && costUsd > 0.0) {
            long deltaMicros = Math.round(costUsd * 1_000_000.0);
            if (deltaMicros > 0) {
                try {
                    intradaySpendMicrosMap.get(spendKey(campaignId), () -> new java.util.concurrent.atomic.AtomicLong(0L))
                            .addAndGet(deltaMicros);
                } catch (java.util.concurrent.ExecutionException ignored) {
                }
            }
        }
    }

    public void refundCampaignSpend(String campaignId, double costUsd) {
        if (campaignId != null && costUsd > 0.0) {
            long deltaMicros = Math.round(costUsd * 1_000_000.0);
            java.util.concurrent.atomic.AtomicLong counter = intradaySpendMicrosMap.getIfPresent(spendKey(campaignId));
            if (counter != null && deltaMicros > 0) {
                counter.updateAndGet(cur -> Math.max(0L, cur - deltaMicros));
            }
        }
    }

    public BudgetPacingController.PacingDecision evaluateCampaignPacing(Campaign campaign, String campaignId) {
        if (budgetPacingController == null) {
            return null;
        }
        String effectiveId = campaignId != null ? campaignId : (campaign != null ? campaign.id() : "default");
        BigDecimal dailyBudget = dailyBudgetOverrides.get(effectiveId);
        if (dailyBudget == null && campaign != null) {
            dailyBudget = campaign.dailyBudget();
        }
        if (dailyBudget == null || dailyBudget.signum() <= 0) {
            return null;
        }
        java.util.concurrent.atomic.AtomicLong spentCounter = intradaySpendMicrosMap.getIfPresent(spendKey(effectiveId));
        double spent = spentCounter != null ? (spentCounter.get() / 1_000_000.0) : 0.0;
        double elapsed = pacingElapsedFractionOverride != null
                ? pacingElapsedFractionOverride
                : (LocalTime.now(ZoneOffset.UTC).toSecondOfDay() / 86400.0);
        return budgetPacingController.evaluatePacing(effectiveId, dailyBudget, BigDecimal.valueOf(spent), elapsed, true);
    }

    public PacingCheckResult checkAndEvaluatePacing(Campaign campaign, String campaignId) {
        BudgetPacingController.PacingDecision decision = evaluateCampaignPacing(campaign, campaignId);
        if (decision == null) {
            return new PacingCheckResult(false, 1.0);
        }
        if (decision.budgetExhausted()
                || (decision.passProbability() < 1.0
                && java.util.concurrent.ThreadLocalRandom.current().nextDouble() > decision.passProbability())) {
            pacingThrottledBids.incrementAndGet();
            return new PacingCheckResult(true, 0.0);
        }
        double mult = decision.bidPacingMultiplier() != null ? decision.bidPacingMultiplier().doubleValue() : 1.0;
        return new PacingCheckResult(false, mult);
    }

    public boolean shouldThrottleByPacing(Campaign campaign, String campaignId) {
        return checkAndEvaluatePacing(campaign, campaignId).throttled();
    }

    /**
     * 解析廣告位的完整软硬双底价决策结果
     */
    public DynamicYieldManager.FloorPriceResult resolveAdaptiveFloorResult(AdSlot slot, OpenRtb.BidRequest request) {
        if (slot == null) {
            return new DynamicYieldManager.FloorPriceResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (yieldManager == null) {
            BigDecimal base = BigDecimal.valueOf(slot.floorPrice());
            return new DynamicYieldManager.FloorPriceResult(base, base);
        }
        String country = extractCountry(request);
        int deviceType = extractDeviceType(request);
        int hour = LocalTime.now(ZoneOffset.UTC).getHour();
        return yieldManager.resolveAdaptiveFloor(slot, country, deviceType, hour);
    }

    /**
     * 基于无锁多维倒排索引 (CampaignInvertedIndex) 与广告组定向规则 (AdGroup) 召回最优候选活动
     */
    public Optional<Campaign> recallBestCampaign(OpenRtb.BidRequest request) {
        TrafficContext ctx = buildTrafficContext(request);
        if (campaignService != null) {
            var adGroupMatches = campaignService.matchAdGroups(ctx);
            if (!adGroupMatches.isEmpty()) {
                dspIndexRecalledBids.incrementAndGet();
                return Optional.of(adGroupMatches.get(0).campaign());
            }
            var campMatches = campaignService.match(ctx);
            if (!campMatches.isEmpty()) {
                dspIndexRecalledBids.incrementAndGet();
                return Optional.of(campMatches.get(0));
            }
        }
        if (campaignInvertedIndex != null) {
            var campMatches = campaignInvertedIndex.match(ctx);
            if (!campMatches.isEmpty()) {
                dspIndexRecalledBids.incrementAndGet();
                return Optional.of(campMatches.get(0));
            }
        }
        return Optional.empty();
    }

    public TrafficContext buildTrafficContext(OpenRtb.BidRequest request) {
        String userId = (request != null && request.user() != null && request.user().id() != null)
                ? request.user().id()
                : "anon";
        String country = extractCountry(request);
        int deviceType = extractDeviceType(request);
        String siteDomain = extractDomain(request);
        java.util.Set<String> mergedSegments = new java.util.HashSet<>();
        if (request != null && request.user() != null && request.user().segments() != null) {
            mergedSegments.addAll(request.user().segments());
        }
        if (traitEngine != null && !"anon".equals(userId)) {
            traitEngine.getSnapshot(userId).ifPresent(snap -> mergedSegments.addAll(snap.dynamicTraits()));
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return new TrafficContext(
                siteDomain,
                deviceType,
                today,
                LocalTime.now(ZoneOffset.UTC).getHour(),
                today.getDayOfWeek(),
                country,
                mergedSegments
        );
    }

    /**
     * Step 1: 计算自适应底价增强后的广告位视图
     */
    public AdSlot enrichSlotWithAdaptiveFloor(AdSlot slot, OpenRtb.BidRequest request) {
        if (slot == null || yieldManager == null) {
            return slot;
        }
        String country = extractCountry(request);
        int deviceType = extractDeviceType(request);
        int hour = LocalTime.now(ZoneOffset.UTC).getHour();
        DynamicYieldManager.FloorPriceResult floorResult =
                yieldManager.resolveAdaptiveFloor(slot, country, deviceType, hour);
        double adaptiveFloor = floorResult.hardFloor() != null
                ? floorResult.hardFloor().doubleValue()
                : slot.floorPrice();
        if (Math.abs(adaptiveFloor - slot.floorPrice()) < 1e-6) {
            return slot;
        }
        return new AdSlot(
                slot.id(),
                slot.name(),
                slot.width(),
                slot.height(),
                adaptiveFloor,
                slot.secure(),
                slot.active(),
                slot.createdAt()
        );
    }

    /**
     * Step 2 & 3: 结合 CDP 用户画像、DSP 深度 pCTR/pCVR 推理与一价 Bid Shading 校准最终竞价价格
     */
    public double calibrateSmartBidPrice(
            OpenRtb.BidRequest request,
            AdSlot slot,
            String advertiser,
            double baseStrategyPrice
    ) {
        return calibrateSmartBidPrice(request, slot, advertiser, baseStrategyPrice, null);
    }

    public double calibrateSmartBidPrice(
            OpenRtb.BidRequest request,
            AdSlot slot,
            String advertiser,
            double baseStrategyPrice,
            Double pacingMultiplierOverride
    ) {
        totalCalibrations.incrementAndGet();
        double floor = slot != null ? slot.floorPrice() : 0.10;
        double valuation = Math.max(floor, baseStrategyPrice);

        String userId = (request != null && request.user() != null && request.user().id() != null)
                ? request.user().id()
                : "anon";
        String country = extractCountry(request);
        int deviceType = extractDeviceType(request);
        String deviceLabel = resolveDeviceLabel(deviceType);
        String siteDomain = extractDomain(request);

        // 1. CDP 实时画像人群溢价校准
        Set<String> userTraits = Set.of();
        if (traitEngine != null && !"anon".equals(userId)) {
            Optional<RealtimeEventTraitEngine.UserBehaviorSnapshot> snapOpt = traitEngine.getSnapshot(userId);
            if (snapOpt.isPresent()) {
                userTraits = snapOpt.get().dynamicTraits();
                if (userTraits.contains("whale_purchaser")) {
                    valuation *= 1.25; // 高价值大 R 用户溢价 25%
                    cdpBoostedBids.incrementAndGet();
                } else if (userTraits.contains("frequent_clicker")) {
                    valuation *= 1.10;
                    cdpBoostedBids.incrementAndGet();
                }
            }
        }

        // 2. DSP 深度 FM 因子分解机 pCTR * pCVR 估值融合（优先召回真实 Campaign 与 AdGroup）
        Campaign matchedCampaign = recallBestCampaign(request).orElse(null);
        if (ctrCvrEngine != null) {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            TrafficContext ctx = new TrafficContext(
                    siteDomain,
                    deviceType,
                    today,
                    LocalTime.now(ZoneOffset.UTC).getHour(),
                    today.getDayOfWeek(),
                    country,
                    userTraits
            );
            Campaign targetCampaign = matchedCampaign != null ? matchedCampaign : new Campaign(
                    "campaign_" + advertiser,
                    advertiser != null && !advertiser.isBlank() ? advertiser : "adv_default",
                    advertiser != null && !advertiser.isBlank() ? advertiser : "adv_default",
                    today.minusDays(7),
                    today.plusDays(30),
                    BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(Math.max(0.5, valuation)),
                    Set.of(),
                    Set.of(),
                    Campaign.Status.ACTIVE,
                    Instant.now()
            );
            DeepCtrCvrInferenceEngine.PredictionResult pred =
                    ctrCvrEngine.predict(ctx, targetCampaign, BigDecimal.valueOf(floor));
            // 结合先验基准 CTR(2.5%) 计算相对价值增益因子 [0.85, 1.35]
            double ctrLift = Math.max(0.85, Math.min(1.35, pred.pCtr() / 0.025));
            valuation = Math.max(floor, valuation * ctrLift);

            // 若配置了 BidModifierEngine，叠加多维倍率并受 Campaign maxBid 约束
            if (bidModifierEngine != null) {
                BigDecimal maxBidLimit = targetCampaign.maxBid() != null && targetCampaign.maxBid().signum() > 0
                        ? targetCampaign.maxBid()
                        : BigDecimal.valueOf(50.0);
                BigDecimal modified = bidModifierEngine.calculateAdjustedBid(
                        BigDecimal.valueOf(valuation),
                        deviceType,
                        country,
                        ctx.hourOfDay(),
                        BigDecimal.ONE,
                        maxBidLimit
                );
                valuation = Math.max(floor, modified.doubleValue());
            }
        }

        // 2.5 DSP PID 日内预算平滑乘数调节（若外部已执行单次 Pacing 评估则直接复用，避免重复步进 PID 状态）
        if (pacingMultiplierOverride != null) {
            valuation = Math.max(floor, valuation * pacingMultiplierOverride);
        } else if (budgetPacingController != null) {
            String effectiveCampId = matchedCampaign != null ? matchedCampaign.id() : ("campaign_" + advertiser);
            BudgetPacingController.PacingDecision pacing = evaluateCampaignPacing(matchedCampaign, effectiveCampId);
            if (pacing != null && !pacing.budgetExhausted() && pacing.bidPacingMultiplier() != null) {
                valuation = Math.max(floor, valuation * pacing.bidPacingMultiplier().doubleValue());
            }
        }

        // 3. DSP 一价拍卖 Bid Shading 最优出价折算（保障绝不低于媒体底价 floor）
        if (bidShadingEngine != null && valuation > floor) {
            String segment = (slot != null ? slot.id() : "slot") + ":" + country + ":" + deviceLabel;
            DynamicBidShadingEngine.ShadingResult shaded = bidShadingEngine.calculateShadedBid(
                    BigDecimal.valueOf(valuation),
                    BigDecimal.valueOf(floor),
                    segment
            );
            if (shaded.shadedBid() != null && shaded.shadedBid().doubleValue() >= floor) {
                double finalPrice = BigDecimal.valueOf(Math.max(floor, shaded.shadedBid().doubleValue()))
                        .setScale(4, RoundingMode.HALF_UP)
                        .doubleValue();
                if (valuation > finalPrice) {
                    totalShadedSavings.add(valuation - finalPrice);
                }
                return finalPrice;
            }
        }

        return BigDecimal.valueOf(Math.max(floor, valuation))
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Step 4: 向 SSP 动态收益管理器回传本次拍卖填充结果
     */
    public void recordSlotOutcome(String slotId, boolean cleared, double winPrice, int candidateCount) {
        if (yieldManager != null && slotId != null) {
            yieldManager.recordAuctionOutcome(slotId, cleared, winPrice, Math.max(1, candidateCount));
        }
    }

    public Map<String, Object> getMetricsSnapshot() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalCalibrations", totalCalibrations.get());
        metrics.put("cdpBoostedBids", cdpBoostedBids.get());
        metrics.put("dspIndexRecalledBids", dspIndexRecalledBids.get());
        metrics.put("pacingThrottledBids", pacingThrottledBids.get());
        metrics.put("totalShadedSavingsUsd", BigDecimal.valueOf(totalShadedSavings.sum())
                .setScale(4, RoundingMode.HALF_UP));
        return metrics;
    }

    public static String resolveDeviceLabel(int devicetype) {
        return switch (devicetype) {
            case 2 -> "desktop";
            case 3 -> "ctv";
            default -> "mobile";
        };
    }

    private static String extractDomain(OpenRtb.BidRequest request) {
        if (request != null && request.site() != null && request.site().domain() != null
                && !request.site().domain().isBlank()) {
            return request.site().domain();
        }
        return "publisher.app";
    }

    private static String extractCountry(OpenRtb.BidRequest request) {
        if (request != null && request.device() != null && request.device().geo() != null
                && !request.device().geo().isBlank()) {
            return request.device().geo();
        }
        return "US";
    }

    private static int extractDeviceType(OpenRtb.BidRequest request) {
        if (request != null && request.device() != null && request.device().devicetype() > 0) {
            return request.device().devicetype();
        }
        return 1;
    }
}
