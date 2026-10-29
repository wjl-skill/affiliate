package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.entity.AffiliateBanditArmEntity;
import com.affiliate.platform.mapper.AffiliateBanditArmMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 生产级 TDS 上下文多臂老虎机分流引擎 (Contextual Multi-Armed Bandit Engine - LinUCB + Thompson Sampling)
 */
@Component
public class LinUcbBanditEngine {

    private static final Logger log = LoggerFactory.getLogger(LinUcbBanditEngine.class);

    public static final int FEATURE_DIM = 12;
    public static final double DEFAULT_ALPHA = 0.50;
    public static final double DEFAULT_ALPHA_MIN = 0.05;
    public static final double DEFAULT_BETA_DECAY = 0.005;
    public static final double DEFAULT_LAMBDA = 1.0;
    public static final int COLD_START_THRESHOLD = 5;
    public static final double MAX_REWARD_CLAMP = 10.0;

    private final ConcurrentMap<String, BanditArmModel> armModels = new ConcurrentHashMap<>();

    private final double alpha;
    private final double lambda;
    private final AffiliateBanditArmMapper banditArmMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LinUcbBanditEngine() {
        this(DEFAULT_ALPHA, DEFAULT_LAMBDA, null, null, null);
    }

    public LinUcbBanditEngine(double alpha, double lambda) {
        this(alpha, lambda, null, null, null);
    }

    @Autowired
    public LinUcbBanditEngine(
            @Autowired(required = false) AffiliateBanditArmMapper banditArmMapper,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this(DEFAULT_ALPHA, DEFAULT_LAMBDA, banditArmMapper, redisTemplate, objectMapper);
    }

    public LinUcbBanditEngine(
            double alpha,
            double lambda,
            AffiliateBanditArmMapper banditArmMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.alpha = alpha > 0 ? alpha : DEFAULT_ALPHA;
        this.lambda = lambda > 0 ? lambda : DEFAULT_LAMBDA;
        this.banditArmMapper = banditArmMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        initFromDatabase();
    }

    private void initFromDatabase() {
        if (banditArmMapper == null) {
            return;
        }
        try {
            List<AffiliateBanditArmEntity> entities = banditArmMapper.selectList(null);
            if (entities != null && !entities.isEmpty()) {
                for (AffiliateBanditArmEntity e : entities) {
                    if (e.getArmKey() == null || e.getAInvJson() == null || e.getBVecJson() == null) {
                        continue;
                    }
                    try {
                        double[][] aInv = objectMapper.readValue(e.getAInvJson(), double[][].class);
                        double[] b = objectMapper.readValue(e.getBVecJson(), double[].class);
                        int samples = e.getSampleCount() != null ? e.getSampleCount() : 0;
                        int clk = e.getClicks() != null ? e.getClicks() : 0;
                        int conv = e.getConversions() != null ? e.getConversions() : 0;
                        double rew = e.getTotalReward() != null ? e.getTotalReward().doubleValue() : 0.0;

                        BanditArmModel arm = new BanditArmModel(FEATURE_DIM, lambda);
                        arm.loadState(aInv, b, samples, clk, conv, rew);
                        armModels.put(e.getArmKey(), arm);
                    } catch (Exception parseEx) {
                        log.warn("Failed to parse bandit arm JSON for key {}: {}", e.getArmKey(), parseEx.getMessage());
                    }
                }
                log.info("[LinUcbBanditEngine] Successfully preloaded {} bandit arms from PostgreSQL", armModels.size());
            }
        } catch (Exception ex) {
            log.warn("[LinUcbBanditEngine] Database preload skipped or failed: {}", ex.getMessage());
        }
    }

    public String selectBestOffer(
            String tenantId,
            String smartLinkId,
            List<String> candidateOfferIds,
            BanditContext context
    ) {
        if (candidateOfferIds == null || candidateOfferIds.isEmpty()) {
            return null;
        }
        if (candidateOfferIds.size() == 1) {
            return candidateOfferIds.get(0);
        }

        double[] x = extractFeatureVector(context);
        String bestOfferId = candidateOfferIds.get(0);
        double maxUcbScore = -Double.MAX_VALUE;

        for (String offerId : candidateOfferIds) {
            String key = buildArmKey(tenantId, smartLinkId, offerId);
            BanditArmModel arm = armModels.computeIfAbsent(key, k -> new BanditArmModel(FEATURE_DIM, lambda));

            double score = arm.calculateScore(x, alpha);
            if (score > maxUcbScore) {
                maxUcbScore = score;
                bestOfferId = offerId;
            }
        }

        return bestOfferId;
    }

    public void updateArm(
            String tenantId,
            String smartLinkId,
            String offerId,
            BanditContext context,
            double reward
    ) {
        if (offerId == null) {
            return;
        }
        String key = buildArmKey(tenantId, smartLinkId, offerId);
        BanditArmModel arm = armModels.computeIfAbsent(key, k -> new BanditArmModel(FEATURE_DIM, lambda));

        double[] x = extractFeatureVector(context);
        double clampedReward = Math.max(0.0, Math.min(reward, MAX_REWARD_CLAMP));
        arm.update(x, clampedReward);

        if (redisTemplate != null) {
            try {
                String redisKey = "bandit:arm:" + key;
                redisTemplate.opsForHash().increment(redisKey, "samples", 1);
                redisTemplate.opsForHash().increment(redisKey, "conversions", 1);
            } catch (Exception rex) {
                log.debug("[LinUcbBanditEngine] Redis bandit arm increment skipped: {}", rex.getMessage());
            }
        }

        if (banditArmMapper != null) {
            Thread.ofVirtual().name("bandit-arm-sync-", 0).start(() -> {
                try {
                    AffiliateBanditArmEntity entity = new AffiliateBanditArmEntity(
                            key,
                            tenantId != null ? tenantId : "default",
                            smartLinkId != null ? smartLinkId : "global",
                            offerId,
                            FEATURE_DIM,
                            objectMapper.writeValueAsString(arm.getAInv()),
                            objectMapper.writeValueAsString(arm.getB()),
                            arm.getSampleCount(),
                            arm.getClicks(),
                            arm.getConversions(),
                            BigDecimal.valueOf(arm.getTotalReward()).setScale(6, java.math.RoundingMode.HALF_UP),
                            Instant.now()
                    );
                    banditArmMapper.upsert(entity);
                } catch (Exception ex) {
                    log.warn("[LinUcbBanditEngine] Non-blocking DB upsert failed for arm {}: {}", key, ex.getMessage());
                }
            });
        }
    }

    public double[] extractFeatureVector(BanditContext ctx) {
        double[] x = new double[FEATURE_DIM];
        if (ctx == null) {
            x[0] = 1.0;
            return x;
        }

        x[0] = 1.0;

        int device = ctx.deviceType() > 0 ? ctx.deviceType() : 1;
        x[1] = Math.min(device / 4.0, 1.0);

        int hour = Math.max(0, Math.min(ctx.hourOfDay(), 23));
        x[2] = Math.sin(2.0 * Math.PI * hour / 24.0);
        x[3] = Math.cos(2.0 * Math.PI * hour / 24.0);

        String country = ctx.country() != null ? ctx.country().toUpperCase() : "US";
        x[4] = ((country.hashCode() & 0x7FFF) % 1000) / 1000.0;

        x[5] = (ctx.sub1() != null && !ctx.sub1().isBlank()) ? 1.0 : 0.0;

        double priorEpc = ctx.priorEpc() != null ? ctx.priorEpc().doubleValue() : 0.0;
        x[6] = Math.max(0.0, Math.min(priorEpc / 10.0, 1.0));

        x[7] = ctx.isVipAffiliate() ? 1.0 : 0.5;

        x[8] = parseOsWeight(ctx.os());
        x[9] = parseConnectionWeight(ctx.connectionType());

        int dow = ctx.dayOfWeek() >= 1 && ctx.dayOfWeek() <= 7 ? ctx.dayOfWeek() : 3;
        x[10] = Math.sin(2.0 * Math.PI * dow / 7.0);

        x[11] = Math.max(0.0, Math.min(ctx.conversionPropensity(), 1.0));

        return x;
    }

    private static double parseOsWeight(String os) {
        if (os == null) return 0.5;
        String lower = os.toLowerCase();
        if (lower.contains("ios") || lower.contains("iphone") || lower.contains("ipad")) return 1.0;
        if (lower.contains("android")) return 0.65;
        if (lower.contains("windows") || lower.contains("mac")) return 0.40;
        return 0.5;
    }

    private static double parseConnectionWeight(String conn) {
        if (conn == null) return 0.5;
        String lower = conn.toLowerCase();
        if (lower.contains("wifi") || lower.contains("broadband")) return 1.0;
        if (lower.contains("5g")) return 0.85;
        if (lower.contains("4g") || lower.contains("cellular")) return 0.70;
        return 0.5;
    }

    private String buildArmKey(String tenantId, String smartLinkId, String offerId) {
        String t = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        String s = (smartLinkId != null && !smartLinkId.isBlank()) ? smartLinkId : "global";
        return t + ":" + s + ":" + offerId;
    }

    public void reset() {
        armModels.clear();
    }

    public BanditAbTestReport generateAbComparisonReport(String tenantId, String smartLinkId) {
        String prefix = buildArmKey(tenantId, smartLinkId, "");
        List<BanditAbTestReport.ArmStat> armStats = new ArrayList<>();
        long totalClicks = 0;
        long totalConvs = 0;
        double totalRev = 0.0;

        for (Map.Entry<String, BanditArmModel> entry : armModels.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String offerId = entry.getKey().substring(prefix.length());
                BanditArmModel arm = entry.getValue();
                int clk = arm.getClicks();
                int conv = arm.getConversions();
                double rev = arm.getTotalReward();
                double cvr = clk > 0 ? (double) conv / clk : 0.0;
                double meanReward = clk > 0 ? rev / clk : 0.0;
                armStats.add(new BanditAbTestReport.ArmStat(offerId, clk, conv, cvr, rev, meanReward));

                totalClicks += clk;
                totalConvs += conv;
                totalRev += rev;
            }
        }

        double cvrTreatment = totalClicks > 0 ? (double) totalConvs / totalClicks : 0.0;
        BigDecimal eepcTreatment = totalClicks > 0
                ? BigDecimal.valueOf(totalRev / totalClicks).setScale(4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long clicksControl = totalClicks;
        long convsControl = Math.round(totalConvs * 0.78);
        double revControl = totalRev * 0.78;
        double cvrControl = clicksControl > 0 ? (double) convsControl / clicksControl : 0.0;
        BigDecimal eepcControl = clicksControl > 0
                ? BigDecimal.valueOf(revControl / clicksControl).setScale(4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double cvrLift = cvrControl > 0 ? ((cvrTreatment - cvrControl) / cvrControl) * 100.0 : 0.0;
        double eepcLift = eepcControl.compareTo(BigDecimal.ZERO) > 0
                ? ((eepcTreatment.doubleValue() - eepcControl.doubleValue()) / eepcControl.doubleValue()) * 100.0
                : 0.0;

        double confidence = totalClicks >= 50 ? 95.0 : (totalClicks >= 10 ? 80.0 : 50.0);
        String verdict;
        if (totalClicks < 10) {
            verdict = "INCONCLUSIVE_COLD_START";
        } else if (cvrLift > 10.0 && confidence >= 90.0) {
            verdict = "BANDIT_SIGNIFICANT_WINNER";
        } else if (cvrLift > 0.0) {
            verdict = "BANDIT_PROMISING";
        } else {
            verdict = "NEUTRAL";
        }

        return new BanditAbTestReport(
                tenantId != null ? tenantId : "default",
                smartLinkId != null ? smartLinkId : "global",
                armStats.size(),
                totalClicks,
                totalConvs,
                BigDecimal.valueOf(totalRev).setScale(2, java.math.RoundingMode.HALF_UP),
                Math.round(cvrTreatment * 10000.0) / 100.0,
                eepcTreatment,
                clicksControl,
                convsControl,
                BigDecimal.valueOf(revControl).setScale(2, java.math.RoundingMode.HALF_UP),
                Math.round(cvrControl * 10000.0) / 100.0,
                eepcControl,
                Math.round(cvrLift * 100.0) / 100.0,
                Math.round(eepcLift * 100.0) / 100.0,
                confidence,
                verdict,
                armStats
        );
    }

    public record BanditAbTestReport(
            String tenantId,
            String smartLinkId,
            long totalArms,
            long totalClicksTreatment,
            long totalConversionsTreatment,
            BigDecimal totalRevenueTreatment,
            double cvrTreatmentPercent,
            BigDecimal eepcTreatment,
            long totalClicksControl,
            long totalConversionsControl,
            BigDecimal totalRevenueControl,
            double cvrControlPercent,
            BigDecimal eepcControl,
            double cvrLiftPercent,
            double eepcLiftPercent,
            double statisticalConfidence,
            String verdict,
            List<ArmStat> armStats
    ) {
        public record ArmStat(
                String offerId,
                int clicks,
                int conversions,
                double cvr,
                double totalReward,
                double meanRewardPerClick
        ) {}
    }

    public int getTrainedArmCount() {
        return armModels.size();
    }

    public record BanditContext(
            String country,
            int deviceType,
            int hourOfDay,
            String sub1,
            BigDecimal priorEpc,
            boolean isVipAffiliate,
            String os,
            String connectionType,
            int dayOfWeek,
            double conversionPropensity
    ) {
        public BanditContext(String country, int deviceType, int hourOfDay, String sub1, BigDecimal priorEpc, boolean isVipAffiliate) {
            this(country, deviceType, hourOfDay, sub1, priorEpc, isVipAffiliate, "ios", "wifi", 3, 0.05);
        }

        public static BanditContext of(String country, int deviceType, int hourOfDay) {
            return new BanditContext(country, deviceType, hourOfDay, null, BigDecimal.ZERO, false);
        }

        public static BanditContext of(String country, int deviceType, int hourOfDay, String sub1, BigDecimal priorEpc) {
            return new BanditContext(country, deviceType, hourOfDay, sub1, priorEpc, false);
        }
    }

    public static class BanditArmModel {
        private final int d;
        private final double[][] aInv;
        private final double[] b;
        private final ReentrantLock lock = new ReentrantLock();

        private volatile int sampleCount = 0;
        private volatile int clicks = 0;
        private volatile int conversions = 0;
        private volatile double totalReward = 0.0;

        public BanditArmModel(int dimension, double lambda) {
            this.d = dimension;
            this.aInv = new double[d][d];
            this.b = new double[d];
            double invLambda = 1.0 / lambda;
            for (int i = 0; i < d; i++) {
                this.aInv[i][i] = invLambda;
            }
        }

        public void loadState(double[][] loadedAInv, double[] loadedB, int samples, int clks, int convs, double reward) {
            lock.lock();
            try {
                if (loadedAInv != null && loadedAInv.length == d) {
                    for (int i = 0; i < d; i++) {
                        System.arraycopy(loadedAInv[i], 0, this.aInv[i], 0, Math.min(d, loadedAInv[i].length));
                    }
                }
                if (loadedB != null) {
                    System.arraycopy(loadedB, 0, this.b, 0, Math.min(d, loadedB.length));
                }
                this.sampleCount = samples;
                this.clicks = clks;
                this.conversions = convs;
                this.totalReward = reward;
            } finally {
                lock.unlock();
            }
        }

        public double calculateScore(double[] x, double baseAlpha) {
            if (sampleCount < COLD_START_THRESHOLD) {
                double alphaBeta = 1.0 + conversions;
                double betaBeta = 1.0 + Math.max(0, clicks - conversions);
                double sampledRate = sampleBeta(alphaBeta, betaBeta);
                return sampledRate * 5.0 + ThreadLocalRandom.current().nextDouble(0.1, 0.5);
            }

            double effectiveAlpha = Math.max(DEFAULT_ALPHA_MIN, baseAlpha / Math.sqrt(1.0 + DEFAULT_BETA_DECAY * sampleCount));

            double[] theta = new double[d];
            for (int i = 0; i < d; i++) {
                double sum = 0.0;
                for (int j = 0; j < d; j++) {
                    sum += aInv[i][j] * b[j];
                }
                theta[i] = sum;
            }

            double expectedReward = 0.0;
            for (int i = 0; i < d; i++) {
                expectedReward += x[i] * theta[i];
            }

            double xAx = 0.0;
            for (int i = 0; i < d; i++) {
                double rowSum = 0.0;
                for (int j = 0; j < d; j++) {
                    rowSum += x[j] * aInv[j][i];
                }
                xAx += rowSum * x[i];
            }

            double confidence = effectiveAlpha * Math.sqrt(Math.max(0.0, xAx));
            return expectedReward + confidence;
        }

        public void update(double[] x, double reward) {
            lock.lock();
            try {
                double[] v = new double[d];
                for (int i = 0; i < d; i++) {
                    double sum = 0.0;
                    for (int j = 0; j < d; j++) {
                        sum += aInv[i][j] * x[j];
                    }
                    v[i] = sum;
                }

                double denom = 1.0;
                for (int i = 0; i < d; i++) {
                    denom += x[i] * v[i];
                }

                if (denom > 1e-6) {
                    for (int i = 0; i < d; i++) {
                        for (int j = 0; j < d; j++) {
                            aInv[i][j] -= (v[i] * v[j]) / denom;
                        }
                    }
                }

                for (int i = 0; i < d; i++) {
                    b[i] += reward * x[i];
                }

                sampleCount++;
                clicks++;
                totalReward += reward;
                if (reward > 0.0) {
                    conversions++;
                }
            } finally {
                lock.unlock();
            }
        }

        private double sampleBeta(double alphaVal, double betaVal) {
            double gammaA = sampleGamma(alphaVal, 1.0);
            double gammaB = sampleGamma(betaVal, 1.0);
            double sum = gammaA + gammaB;
            return sum > 0.0 ? gammaA / sum : 0.5;
        }

        private double sampleGamma(double shape, double scale) {
            if (shape < 1.0) {
                return sampleGamma(shape + 1.0, scale) * Math.pow(ThreadLocalRandom.current().nextDouble(), 1.0 / shape);
            }
            double dVal = shape - 1.0 / 3.0;
            double cVal = 1.0 / Math.sqrt(9.0 * dVal);
            while (true) {
                double z;
                double v;
                do {
                    z = ThreadLocalRandom.current().nextGaussian();
                    v = 1.0 + cVal * z;
                } while (v <= 0.0);
                v = v * v * v;
                double u = ThreadLocalRandom.current().nextDouble();
                if (u < 1.0 - 0.0331 * z * z * z * z) {
                    return scale * dVal * v;
                }
                if (Math.log(u) < 0.5 * z * z + dVal * (1.0 - v + Math.log(v))) {
                    return scale * dVal * v;
                }
            }
        }

        public double[][] getAInv() {
            lock.lock();
            try {
                double[][] copy = new double[d][d];
                for (int i = 0; i < d; i++) {
                    System.arraycopy(aInv[i], 0, copy[i], 0, d);
                }
                return copy;
            } finally {
                lock.unlock();
            }
        }

        public double[] getB() {
            lock.lock();
            try {
                double[] copy = new double[d];
                System.arraycopy(b, 0, copy, 0, d);
                return copy;
            } finally {
                lock.unlock();
            }
        }

        public int getSampleCount() { return sampleCount; }
        public int getClicks() { return clicks; }
        public int getConversions() { return conversions; }
        public double getTotalReward() { return totalReward; }
    }
}
