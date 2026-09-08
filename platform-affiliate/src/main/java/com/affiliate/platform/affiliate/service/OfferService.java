package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.domain.OfferTierPayout;
import com.affiliate.platform.affiliate.repository.OfferGoalRepository;
import com.affiliate.platform.affiliate.repository.OfferRepository;
import com.affiliate.platform.affiliate.repository.OfferTierPayoutRepository;
import com.affiliate.platform.cache.CacheKeyGenerator;
import com.affiliate.platform.cache.MultiLevelCacheManager;
import com.affiliate.platform.entity.OfferEntity;
import com.affiliate.platform.entity.OfferGoalEntity;
import com.affiliate.platform.entity.OfferTierPayoutEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网盟推广计划全生命周期与配额管控服务 (Offer & Cap Service - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL `affiliate_offer`、`affiliate_offer_tier_payout` 与 `affiliate_offer_goal` 表，
 * 引入 Guava + Redis 两级缓存加速（防缓存击穿与雪崩），并支持实时的日转化上限扣减与保底 Offer 智能分流。
 */
@Service
public class OfferService {

    private final OfferRepository offerRepository;
    private final OfferTierPayoutRepository tierPayoutRepository;
    private final OfferGoalRepository offerGoalRepository;
    private final MultiLevelCacheManager cacheManager;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    private final ConcurrentMap<String, AtomicInteger> dailyConversionCounts = new ConcurrentHashMap<>();

    public OfferService(
            OfferRepository offerRepository,
            OfferTierPayoutRepository tierPayoutRepository,
            OfferGoalRepository offerGoalRepository,
            MultiLevelCacheManager cacheManager,
            CacheKeyGenerator keyGenerator,
            ObjectMapper objectMapper
    ) {
        this.offerRepository = offerRepository;
        this.tierPayoutRepository = tierPayoutRepository;
        this.offerGoalRepository = offerGoalRepository;
        this.cacheManager = cacheManager;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存或更新推广计划至 PostgreSQL
     */
    @Transactional
    public Offer save(Offer offer) {
        OfferEntity entity = new OfferEntity(
                offer.id(),
                offer.tenantId(),
                offer.advertiserId(),
                offer.title(),
                offer.landingPageUrl(),
                offer.payoutType().name(),
                offer.defaultPayout(),
                offer.defaultRevenue(),
                offer.status().name(),
                offer.dailyConversionCap(),
                offer.dailyRevenueCap(),
                offer.fallbackOfferId(),
                offer.expiresAt(),
                offer.createdAt() != null ? offer.createdAt() : Instant.now()
        );

        offerRepository.save(entity);

        String cacheKey = keyGenerator.offerById(offer.id());
        cacheManager.put(cacheKey, offer, Duration.ofMinutes(30));

        return offer;
    }

    /**
     * 根据 ID 检索 Offer（两级缓存加速）
     */
    public Optional<Offer> find(String id) {
        if (id == null) return Optional.empty();
        if (offerCache != null) {
            Offer cached = offerCache.get(id, k -> {
                if (offerMapper != null) {
                    OfferEntity entity = offerMapper.selectById(k);
                    return entity != null ? toDomain(entity) : null;
                }
                return fallbackOffers.get(k);
            });
            return Optional.ofNullable(cached);
        }

        if (offerMapper != null) {
            OfferEntity entity = offerMapper.selectById(id);
            return Optional.ofNullable(entity).map(this::toDomain);
        }
        return Optional.ofNullable(fallbackOffers.get(id));
    }

    /**
     * 配置渠道或等级的专属阶梯出价
     */
    public void addTierPayout(OfferTierPayout payout) {
        if (tierPayoutMapper != null) {
            OfferTierPayoutEntity entity = new OfferTierPayoutEntity(
                    null,
                    payout.offerId(),
                    payout.affiliateId(),
                    payout.targetTier() != null ? payout.targetTier().name() : null,
                    payout.customPayout(),
                    payout.customRevenue(),
                    Instant.now()
            );
            tierPayoutMapper.insert(entity);
            return;
        }

        fallbackTierPayouts.computeIfAbsent(payout.offerId(), k -> new ArrayList<>()).add(payout);
    }

    /**
     * 裁决渠道在特定 Offer 上的最终生效佣金与平台营收
     */
    public PayoutResolution resolvePayout(Offer offer, AffiliatePartner affiliate) {
        if (offer == null) {
            return new PayoutResolution(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<OfferTierPayout> customList = getTierPayouts(offer.id());
        if (customList != null && affiliate != null) {
            for (OfferTierPayout rule : customList) {
                if (rule.affiliateId() != null && affiliate.id().equalsIgnoreCase(rule.affiliateId())) {
                    return new PayoutResolution(rule.customPayout(), rule.customRevenue());
                }
            }
            for (OfferTierPayout rule : customList) {
                if (rule.targetTier() != null && rule.targetTier() == affiliate.tier()) {
                    return new PayoutResolution(rule.customPayout(), rule.customRevenue());
                }
            }
        }

        return new PayoutResolution(offer.defaultPayout(), offer.defaultRevenue());
    }

    public void addGoal(com.affiliate.platform.affiliate.domain.OfferGoal goal) {
        if (goal == null || goal.offerId() == null) return;
        if (offerGoalMapper != null) {
            OfferGoalEntity entity = new OfferGoalEntity(
                    goal.goalId(),
                    "default",
                    goal.offerId(),
                    goal.name(),
                    goal.eventType().name(),
                    "CPA",
                    goal.payout(),
                    goal.revenue(),
                    "ACTIVE",
                    Instant.now(),
                    Instant.now()
            );
            if (offerGoalMapper.selectById(goal.goalId()) != null) {
                offerGoalMapper.updateById(entity);
            } else {
                offerGoalMapper.insert(entity);
            }
        }
        offerGoals.compute(goal.offerId(), (k, list) -> {
            List<com.affiliate.platform.affiliate.domain.OfferGoal> newList = list == null ? new ArrayList<>() : new ArrayList<>(list);
            newList.removeIf(g -> g.goalId().equals(goal.goalId()));
            newList.add(goal);
            return newList;
        });
    }

    public Optional<com.affiliate.platform.affiliate.domain.OfferGoal> findGoal(String offerId, String goalId) {
        if (offerId == null || goalId == null) return Optional.empty();
        if (offerGoalMapper != null) {
            OfferGoalEntity entity = offerGoalMapper.selectById(goalId);
            if (entity != null && offerId.equalsIgnoreCase(entity.getOfferId())) {
                return Optional.of(toGoalDomain(entity));
            }
        }
        List<com.affiliate.platform.affiliate.domain.OfferGoal> goals = offerGoals.get(offerId);
        if (goals == null) return Optional.empty();
        return goals.stream().filter(g -> g.goalId().equals(goalId)).findFirst();
    }

    public List<com.affiliate.platform.affiliate.domain.OfferGoal> listGoals(String offerId) {
        if (offerId == null) return List.of();
        if (offerGoalMapper != null) {
            QueryWrapper<OfferGoalEntity> qw = new QueryWrapper<>();
            qw.eq("offer_id", offerId).eq("status", "ACTIVE");
            List<OfferGoalEntity> entities = offerGoalMapper.selectList(qw);
            if (!entities.isEmpty()) {
                return entities.stream().map(this::toGoalDomain).toList();
            }
        }
        List<com.affiliate.platform.affiliate.domain.OfferGoal> goals = offerGoals.get(offerId);
        return goals == null ? List.of() : List.copyOf(goals);
    }

    public boolean removeGoal(String offerId, String goalId) {
        if (offerId == null || goalId == null) return false;
        if (offerGoalMapper != null) {
            offerGoalMapper.deleteById(goalId);
        }
        List<com.affiliate.platform.affiliate.domain.OfferGoal> list = offerGoals.get(offerId);
        if (list != null) {
            return list.removeIf(g -> g.goalId().equals(goalId));
        }
        return true;
    }

    private com.affiliate.platform.affiliate.domain.OfferGoal toGoalDomain(OfferGoalEntity e) {
        com.affiliate.platform.affiliate.domain.OfferGoal.EventType type = com.affiliate.platform.affiliate.domain.OfferGoal.EventType.INSTALL;
        try {
            if (e.getGoalType() != null) {
                type = com.affiliate.platform.affiliate.domain.OfferGoal.EventType.valueOf(e.getGoalType());
            }
        } catch (Exception ignored) {}

        return new com.affiliate.platform.affiliate.domain.OfferGoal(
                e.getId(),
                e.getOfferId(),
                e.getGoalName(),
                type,
                e.getPayout(),
                e.getRevenue(),
                false,
                true,
                30
        );
    }

    public boolean incrementAndCheckCap(String offerId, String dateKey) {
        Optional<Offer> opt = find(offerId);
        if (opt.isEmpty() || opt.get().dailyConversionCap() <= 0) {
            return true;
        }

        String counterKey = offerId + ":" + dateKey;
        AtomicInteger counter = dailyConversionCounts.computeIfAbsent(counterKey, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();

        return current <= opt.get().dailyConversionCap();
    }

    public boolean isCapReached(String offerId, String dateKey) {
        Optional<Offer> opt = find(offerId);
        if (opt.isEmpty() || opt.get().dailyConversionCap() <= 0) {
            return false;
        }
        String counterKey = offerId + ":" + dateKey;
        AtomicInteger counter = dailyConversionCounts.get(counterKey);
        return counter != null && counter.get() >= opt.get().dailyConversionCap();
    }

    public Offer resolveActiveOfferWithFallback(String offerId, String dateKey) {
        Optional<Offer> opt = find(offerId);
        if (opt.isEmpty()) {
            return null;
        }
        Offer offer = opt.get();

        if (!offer.isAvailable() || isCapReached(offerId, dateKey)) {
            if (offer.fallbackOfferId() != null && !offer.fallbackOfferId().isBlank()) {
                Optional<Offer> fallbackOpt = find(offer.fallbackOfferId());
                if (fallbackOpt.isPresent() && fallbackOpt.get().isAvailable()) {
                    return fallbackOpt.get();
                }
            }
            return null;
        }

        return offer;
    }

    public List<Offer> list() {
        if (offerMapper != null) {
            QueryWrapper<OfferEntity> qw = new QueryWrapper<>();
            qw.orderByDesc("created_at").last("LIMIT 1000");
            List<OfferEntity> entities = offerMapper.selectList(qw);
            return entities.stream().map(this::toDomain).toList();
        }
        return List.copyOf(fallbackOffers.values());
    }

    public List<OfferTierPayout> getTierPayouts(String offerId) {
        if (tierPayoutMapper != null) {
            QueryWrapper<OfferTierPayoutEntity> qw = new QueryWrapper<>();
            qw.eq("offer_id", offerId);
            List<OfferTierPayoutEntity> list = tierPayoutMapper.selectList(qw);
            return list.stream().map(this::toTierPayoutDomain).toList();
        }
        List<OfferTierPayout> list = fallbackTierPayouts.get(offerId);
        return list == null ? List.of() : List.copyOf(list);
    }

    private Offer toDomain(OfferEntity e) {
        return new Offer(
                e.getId(),
                e.getTenantId(),
                e.getAdvertiserId(),
                e.getTitle(),
                e.getLandingPageUrl(),
                Offer.PayoutType.valueOf(e.getPayoutType()),
                e.getDefaultPayout(),
                e.getDefaultRevenue(),
                Offer.Status.valueOf(e.getStatus()),
                e.getDailyConversionCap() != null ? e.getDailyConversionCap() : 0,
                e.getDailyRevenueCap(),
                e.getFallbackOfferId(),
                Set.of(),
                Set.of(),
                e.getExpiresAt(),
                e.getCreatedAt()
        );
    }

    private OfferTierPayout toTierPayoutDomain(OfferTierPayoutEntity e) {
        AffiliatePartner.Tier tier = null;
        if (e.getTargetTier() != null) {
            try {
                tier = AffiliatePartner.Tier.valueOf(e.getTargetTier());
            } catch (Exception ignored) {}
        }
        return new OfferTierPayout(
                e.getOfferId(),
                e.getAffiliateId(),
                tier,
                e.getCustomPayout(),
                e.getCustomRevenue()
        );
    }

    public record PayoutResolution(BigDecimal payout, BigDecimal revenue) {}
}
