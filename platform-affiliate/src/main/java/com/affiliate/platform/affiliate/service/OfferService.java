package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.OfferGoal;
import com.affiliate.platform.affiliate.domain.OfferTierPayout;
import com.affiliate.platform.entity.OfferEntity;
import com.affiliate.platform.entity.OfferGoalEntity;
import com.affiliate.platform.entity.OfferTierPayoutEntity;
import com.affiliate.platform.mapper.OfferGoalMapper;
import com.affiliate.platform.mapper.OfferMapper;
import com.affiliate.platform.mapper.OfferTierPayoutMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Offer 生命周期、阶梯佣金、转化目标和日 Cap 服务。 */
@Service
public class OfferService {
    private final OfferMapper offerMapper;
    private final OfferGoalMapper goalMapper;
    private final OfferTierPayoutMapper tierMapper;
    private final ConcurrentMap<String, Offer> fallbackOffers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<OfferGoal>> fallbackGoals = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<OfferTierPayout>> fallbackTierPayouts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> dailyConversionCounts = new ConcurrentHashMap<>();

    public OfferService() { this(null, null, null); }

    public OfferService(OfferMapper offerMapper, OfferGoalMapper goalMapper, OfferTierPayoutMapper tierMapper) {
        this.offerMapper = offerMapper;
        this.goalMapper = goalMapper;
        this.tierMapper = tierMapper;
    }

    @Autowired
    public OfferService(@Autowired(required = false) OfferMapper offerMapper,
                        @Autowired(required = false) OfferGoalMapper goalMapper,
                        @Autowired(required = false) OfferTierPayoutMapper tierMapper,
                        @Autowired(required = false) com.fasterxml.jackson.databind.ObjectMapper ignored) {
        this(offerMapper, goalMapper, tierMapper);
    }

    @Transactional
    public Offer save(Offer offer) {
        if (offer == null || offer.id() == null || offer.id().isBlank()) throw new IllegalArgumentException("offer id must not be blank");
        Offer normalized = offer.createdAt() == null ? new Offer(offer.id(), offer.tenantId(), offer.advertiserId(), offer.title(), offer.landingPageUrl(), offer.payoutType(), offer.defaultPayout(), offer.defaultRevenue(), offer.status(), offer.dailyConversionCap(), offer.dailyRevenueCap(), offer.fallbackOfferId(), offer.allowedCountries(), offer.allowedDevices(), offer.expiresAt(), Instant.now()) : offer;
        if (offerMapper != null) {
            OfferEntity entity = new OfferEntity(normalized.id(), normalized.tenantId(), normalized.advertiserId(), normalized.title(), normalized.landingPageUrl(), normalized.payoutType().name(), normalized.defaultPayout(), normalized.defaultRevenue(), normalized.status().name(), normalized.dailyConversionCap(), normalized.dailyRevenueCap(), normalized.fallbackOfferId(), normalized.expiresAt(), normalized.createdAt());
            if (offerMapper.selectById(normalized.id()) == null) offerMapper.insert(entity); else offerMapper.updateById(entity);
        }
        fallbackOffers.put(normalized.id(), normalized);
        return normalized;
    }

    public Optional<Offer> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        if (offerMapper != null) { OfferEntity row = offerMapper.selectById(id); if (row != null) return Optional.of(toDomain(row)); }
        return Optional.ofNullable(fallbackOffers.get(id));
    }

    public List<Offer> list() {
        if (offerMapper != null) { QueryWrapper<OfferEntity> q = new QueryWrapper<>(); q.orderByDesc("created_at").last("LIMIT 1000"); List<OfferEntity> rows = offerMapper.selectList(q); if (rows != null && !rows.isEmpty()) return rows.stream().map(this::toDomain).toList(); }
        return List.copyOf(fallbackOffers.values());
    }

    public void addTierPayout(OfferTierPayout payout) {
        if (payout == null || payout.offerId() == null) return;
        if (tierMapper != null) tierMapper.insert(new OfferTierPayoutEntity(null, payout.offerId(), payout.affiliateId(), payout.targetTier() == null ? null : payout.targetTier().name(), payout.customPayout(), payout.customRevenue(), Instant.now()));
        fallbackTierPayouts.compute(payout.offerId(), (k, old) -> { List<OfferTierPayout> copy = old == null ? new ArrayList<>() : new ArrayList<>(old); copy.removeIf(x -> java.util.Objects.equals(x.affiliateId(), payout.affiliateId()) && java.util.Objects.equals(x.targetTier(), payout.targetTier())); copy.add(payout); return copy; });
    }

    public PayoutResolution resolvePayout(Offer offer, AffiliatePartner affiliate) {
        if (offer == null) return new PayoutResolution(BigDecimal.ZERO, BigDecimal.ZERO);
        for (OfferTierPayout rule : getTierPayouts(offer.id())) {
            if (affiliate != null && rule.affiliateId() != null && rule.affiliateId().equalsIgnoreCase(affiliate.id())) return new PayoutResolution(rule.customPayout(), rule.customRevenue());
        }
        for (OfferTierPayout rule : getTierPayouts(offer.id())) {
            if (affiliate != null && rule.targetTier() != null && rule.targetTier() == affiliate.tier()) return new PayoutResolution(rule.customPayout(), rule.customRevenue());
        }
        return new PayoutResolution(offer.defaultPayout(), offer.defaultRevenue());
    }

    public void addGoal(OfferGoal goal) {
        if (goal == null || goal.offerId() == null || goal.goalId() == null) return;
        if (goalMapper != null) { Instant now = Instant.now(); OfferGoalEntity row = new OfferGoalEntity(goal.goalId(), "default", goal.offerId(), goal.name(), goal.eventType().name(), "CPA", goal.payout(), goal.revenue(), "ACTIVE", now, now); if (goalMapper.selectById(goal.goalId()) == null) goalMapper.insert(row); else goalMapper.updateById(row); }
        fallbackGoals.compute(goal.offerId(), (k, old) -> { List<OfferGoal> copy = old == null ? new ArrayList<>() : new ArrayList<>(old); copy.removeIf(x -> x.goalId().equals(goal.goalId())); copy.add(goal); return copy; });
    }

    public Optional<OfferGoal> findGoal(String offerId, String goalId) {
        if (offerId == null || goalId == null) return Optional.empty();
        if (goalMapper != null) { OfferGoalEntity row = goalMapper.selectById(goalId); if (row != null && offerId.equals(row.getOfferId()) && !"DELETED".equals(row.getStatus())) return Optional.of(toGoalDomain(row)); }
        return fallbackGoals.getOrDefault(offerId, List.of()).stream().filter(x -> x.goalId().equals(goalId)).findFirst();
    }

    public List<OfferGoal> listGoals(String offerId) {
        if (offerId == null) return List.of();
        if (goalMapper != null) { QueryWrapper<OfferGoalEntity> q = new QueryWrapper<>(); q.eq("offer_id", offerId).eq("status", "ACTIVE").orderByDesc("created_at"); List<OfferGoalEntity> rows = goalMapper.selectList(q); if (rows != null && !rows.isEmpty()) return rows.stream().map(this::toGoalDomain).toList(); }
        return List.copyOf(fallbackGoals.getOrDefault(offerId, List.of()));
    }

    public boolean removeGoal(String offerId, String goalId) {
        if (offerId == null || goalId == null) return false; boolean existed = findGoal(offerId, goalId).isPresent();
        if (goalMapper != null && existed) { OfferGoalEntity row = goalMapper.selectById(goalId); if (row != null) { row.setStatus("DELETED"); row.setUpdatedAt(Instant.now()); goalMapper.updateById(row); } }
        fallbackGoals.computeIfPresent(offerId, (k, list) -> { list.removeIf(x -> x.goalId().equals(goalId)); return list; }); return existed;
    }

    public boolean incrementAndCheckCap(String offerId, String dateKey) {
        Optional<Offer> offer = find(offerId); if (offer.isEmpty() || offer.get().dailyConversionCap() <= 0) return true;
        int current = dailyConversionCounts.computeIfAbsent(offerId + ":" + dateKey, k -> new AtomicInteger()).incrementAndGet(); return current <= offer.get().dailyConversionCap();
    }

    public boolean isCapReached(String offerId, String dateKey) { Optional<Offer> offer = find(offerId); if (offer.isEmpty() || offer.get().dailyConversionCap() <= 0) return false; AtomicInteger count = dailyConversionCounts.get(offerId + ":" + dateKey); return count != null && count.get() >= offer.get().dailyConversionCap(); }

    public Offer resolveActiveOfferWithFallback(String offerId, String dateKey) {
        Optional<Offer> candidate = find(offerId); if (candidate.isPresent() && candidate.get().isAvailable() && !isCapReached(offerId, dateKey)) return candidate.get();
        if (candidate.isPresent() && candidate.get().fallbackOfferId() != null) { Optional<Offer> fallback = find(candidate.get().fallbackOfferId()); if (fallback.isPresent() && fallback.get().isAvailable() && !isCapReached(fallback.get().id(), dateKey)) return fallback.get(); }
        return null;
    }

    public List<OfferTierPayout> getTierPayouts(String offerId) {
        if (offerId == null) return List.of();
        if (tierMapper != null) { QueryWrapper<OfferTierPayoutEntity> q = new QueryWrapper<>(); q.eq("offer_id", offerId).orderByDesc("created_at"); List<OfferTierPayoutEntity> rows = tierMapper.selectList(q); if (rows != null && !rows.isEmpty()) return rows.stream().map(this::toTierPayoutDomain).toList(); }
        return List.copyOf(fallbackTierPayouts.getOrDefault(offerId, List.of()));
    }

    private Offer toDomain(OfferEntity e) { return new Offer(e.getId(), e.getTenantId(), e.getAdvertiserId(), e.getTitle(), e.getLandingPageUrl(), Offer.PayoutType.valueOf(e.getPayoutType()), e.getDefaultPayout(), e.getDefaultRevenue(), Offer.Status.valueOf(e.getStatus()), e.getDailyConversionCap() == null ? 0 : e.getDailyConversionCap(), e.getDailyRevenueCap(), e.getFallbackOfferId(), Set.of(), Set.of(), e.getExpiresAt(), e.getCreatedAt()); }
    private OfferGoal toGoalDomain(OfferGoalEntity e) { OfferGoal.EventType type; try { type = OfferGoal.EventType.valueOf(e.getGoalType()); } catch (Exception ignored) { type = OfferGoal.EventType.CUSTOM; } return new OfferGoal(e.getId(), e.getOfferId(), e.getGoalName(), type, e.getPayout(), e.getRevenue(), false, true, 30); }
    private OfferTierPayout toTierPayoutDomain(OfferTierPayoutEntity e) { AffiliatePartner.Tier tier = null; try { if (e.getTargetTier() != null) tier = AffiliatePartner.Tier.valueOf(e.getTargetTier()); } catch (Exception ignored) { } return new OfferTierPayout(e.getOfferId(), e.getAffiliateId(), tier, e.getCustomPayout(), e.getCustomRevenue()); }
    public record PayoutResolution(BigDecimal payout, BigDecimal revenue) { }
}
