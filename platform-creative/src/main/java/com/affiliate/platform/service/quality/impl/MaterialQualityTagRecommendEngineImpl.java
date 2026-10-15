package com.affiliate.platform.service.quality.impl;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import com.affiliate.platform.domain.quality.MaterialPerformance7D;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.quality.MaterialQualityResult;
import com.affiliate.platform.domain.quality.MaterialSampleStatus;
import com.affiliate.platform.service.quality.MaterialQualityTagRecommendEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 素材质量标签评估推荐引擎标准实现
 * <p>
 * 遵循严格短路判定顺序：
 * 1. UNRATED (未匹配或样本不足)
 * 2. STAR (明星素材：高回收、低成本、已放量)
 * 3. POTENTIAL (潜力素材：高回收、具备放量空间或点击优异)
 * 4. BLACKHOLE (黑洞素材：低回收、高成本)
 * 5. RISK (风险素材：回收偏弱或前/后链路双低)
 * 6. CORE (主力素材：样本充足平稳兜底)
 */
@Service
public class MaterialQualityTagRecommendEngineImpl implements MaterialQualityTagRecommendEngine {

    private static final Logger log = LoggerFactory.getLogger(MaterialQualityTagRecommendEngineImpl.class);

    @Override
    public MaterialQualityResult evaluate(MaterialPerformance7D performance, MaterialBenchmark7D benchmark) {
        if (performance == null) {
            throw new IllegalArgumentException("performance metrics must not be null");
        }
        if (benchmark == null) {
            throw new IllegalArgumentException("benchmark must not be null");
        }

        List<String> reasonTags = new ArrayList<>();
        List<String> ruleHits = new ArrayList<>();

        String creativeId = performance.creativeId() != null ? performance.creativeId() : performance.materialId();
        String materialName = performance.materialName() != null ? performance.materialName() : "Unknown Material";
        LocalDate statDate = performance.statDate() != null ? performance.statDate() : benchmark.statDate();
        String tenantId = benchmark.tenantId() != null ? benchmark.tenantId() : "public";

        // 1. 判断是否未匹配到投放数据
        if (isUnmatched(performance, reasonTags, ruleHits)) {
            return buildResult(
                    tenantId, creativeId, materialName, statDate,
                    MaterialQualityLevel.UNRATED, MaterialSampleStatus.UNMATCHED,
                    MaterialQualityLevel.UNRATED.getDefaultScore(),
                    reasonTags, ruleHits,
                    "未在广告事实表中匹配到该素材的投放数据，请检查物料命名或等值映射",
                    performance, benchmark
            );
        }

        // 2. 判断样本是否不足 (三重门槛过滤)
        if (isInsufficientSample(performance, benchmark, reasonTags, ruleHits)) {
            return buildResult(
                    tenantId, creativeId, materialName, statDate,
                    MaterialQualityLevel.UNRATED, MaterialSampleStatus.INSUFFICIENT,
                    MaterialQualityLevel.UNRATED.getDefaultScore(),
                    reasonTags, ruleHits,
                    "素材7日累计数据未达到有效样本门槛，暂不予评星，建议继续投放积累数据",
                    performance, benchmark
            );
        }

        // 样本充足，进入星级评定
        MaterialSampleStatus sampleStatus = MaterialSampleStatus.SUFFICIENT;

        // 3. 判断是否命中明星素材 (STAR)
        if (isStar(performance, benchmark, reasonTags, ruleHits)) {
            return buildResult(
                    tenantId, creativeId, materialName, statDate,
                    MaterialQualityLevel.STAR, sampleStatus,
                    MaterialQualityLevel.STAR.getDefaultScore(),
                    reasonTags, ruleHits,
                    MaterialQualityLevel.STAR.getDefaultAdvice(),
                    performance, benchmark
            );
        }

        // 4. 判断是否命中潜力素材 (POTENTIAL)
        if (isPotential(performance, benchmark, reasonTags, ruleHits)) {
            return buildResult(
                    tenantId, creativeId, materialName, statDate,
                    MaterialQualityLevel.POTENTIAL, sampleStatus,
                    MaterialQualityLevel.POTENTIAL.getDefaultScore(),
                    reasonTags, ruleHits,
                    MaterialQualityLevel.POTENTIAL.getDefaultAdvice(),
                    performance, benchmark
            );
        }

        // 5. 判断是否命中黑洞素材 (BLACKHOLE)
        if (isBlackhole(performance, benchmark, reasonTags, ruleHits)) {
            return buildResult(
                    tenantId, creativeId, materialName, statDate,
                    MaterialQualityLevel.BLACKHOLE, sampleStatus,
                    MaterialQualityLevel.BLACKHOLE.getDefaultScore(),
                    reasonTags, ruleHits,
                    MaterialQualityLevel.BLACKHOLE.getDefaultAdvice(),
                    performance, benchmark
            );
        }

        // 6. 判断是否命中风险素材 (RISK)
        if (isRisk(performance, benchmark, reasonTags, ruleHits)) {
            return buildResult(
                    tenantId, creativeId, materialName, statDate,
                    MaterialQualityLevel.RISK, sampleStatus,
                    MaterialQualityLevel.RISK.getDefaultScore(),
                    reasonTags, ruleHits,
                    MaterialQualityLevel.RISK.getDefaultAdvice(),
                    performance, benchmark
            );
        }

        // 7. 兜底判定为主力素材 (CORE)
        reasonTags.add("STABLE_PERFORMANCE");
        reasonTags.add("CORE_BACKBONE");
        ruleHits.add("CORE: sample sufficient and neither star/potential nor blackhole/risk");

        return buildResult(
                tenantId, creativeId, materialName, statDate,
                MaterialQualityLevel.CORE, sampleStatus,
                MaterialQualityLevel.CORE.getDefaultScore(),
                reasonTags, ruleHits,
                MaterialQualityLevel.CORE.getDefaultAdvice(),
                performance, benchmark
        );
    }

    /**
     * 判断是否未匹配到投放事实
     */
    private boolean isUnmatched(MaterialPerformance7D p, List<String> reasonTags, List<String> ruleHits) {
        boolean noAdName = (p.adName() == null || p.adName().isBlank());
        boolean zeroMetrics = (p.promotionCost().compareTo(BigDecimal.ZERO) == 0
                && p.clicks() == 0
                && p.impressions() == 0
                && p.installNum() == 0);

        if (noAdName && zeroMetrics) {
            reasonTags.add("UNMATCHED_AD_DATA");
            ruleHits.add("UNMATCHED: missing adName and all metrics are zero");
            return true;
        }
        return false;
    }

    /**
     * 判断是否属于小样本 (三重有效样本保护门槛)
     */
    private boolean isInsufficientSample(MaterialPerformance7D p, MaterialBenchmark7D b,
                                         List<String> reasonTags, List<String> ruleHits) {
        BigDecimal costThreshold = b.sampleCostThreshold() != null ? b.sampleCostThreshold() : MaterialBenchmark7D.DEFAULT_COST_THRESHOLD;
        long clickThreshold = b.sampleClickThreshold() > 0 ? b.sampleClickThreshold() : MaterialBenchmark7D.DEFAULT_CLICK_THRESHOLD;
        long installThreshold = b.sampleInstallThreshold();

        boolean costInsufficient = p.promotionCost().compareTo(costThreshold) < 0;
        boolean clickInsufficient = p.clicks() < clickThreshold;
        boolean installInsufficient = (installThreshold > 0 && p.installNum() < installThreshold);

        if (costInsufficient || clickInsufficient || installInsufficient) {
            if (costInsufficient) {
                reasonTags.add("INSUFFICIENT_COST");
                ruleHits.add(String.format("SAMPLE_GUARD: promotionCost(%s) < costThreshold(%s)", p.promotionCost(), costThreshold));
            }
            if (clickInsufficient) {
                reasonTags.add("INSUFFICIENT_CLICKS");
                ruleHits.add(String.format("SAMPLE_GUARD: clicks(%d) < clickThreshold(%d)", p.clicks(), clickThreshold));
            }
            if (installInsufficient) {
                reasonTags.add("INSUFFICIENT_INSTALLS");
                ruleHits.add(String.format("SAMPLE_GUARD: installNum(%d) < installThreshold(%d)", p.installNum(), installThreshold));
            }
            return true;
        }
        return false;
    }

    /**
     * 判定明星素材 (STAR)
     * 条件：ROAS >= roasP75 && CPI <= cpiAvg && Cost >= costP50
     */
    private boolean isStar(MaterialPerformance7D p, MaterialBenchmark7D b,
                           List<String> reasonTags, List<String> ruleHits) {
        BigDecimal roas = p.roas();
        BigDecimal cpi = p.cpi();
        BigDecimal cost = p.promotionCost();

        boolean roasHigh = roas.compareTo(b.roasP75()) >= 0;
        boolean cpiGood = cpi.compareTo(b.cpiAvg()) <= 0;
        boolean scaleHigh = cost.compareTo(b.costP50()) >= 0;

        if (roasHigh && cpiGood && scaleHigh) {
            reasonTags.add("HIGH_ROAS");
            reasonTags.add("LOW_CPI");
            reasonTags.add("HIGH_SCALE");
            ruleHits.add(String.format("STAR: roas(%s) >= p75(%s) && cpi(%s) <= avg(%s) && cost(%s) >= p50(%s)",
                    roas, b.roasP75(), cpi, b.cpiAvg(), cost, b.costP50()));
            return true;
        }
        return false;
    }

    /**
     * 判定潜力素材 (POTENTIAL)
     * 条件：ROAS >= roasP75 && (Cost < costP50 || CTR >= ctrAvg)
     */
    private boolean isPotential(MaterialPerformance7D p, MaterialBenchmark7D b,
                                List<String> reasonTags, List<String> ruleHits) {
        BigDecimal roas = p.roas();
        BigDecimal cost = p.promotionCost();
        BigDecimal ctr = p.ctr();

        boolean roasHigh = roas.compareTo(b.roasP75()) >= 0;
        boolean costLow = cost.compareTo(b.costP50()) < 0;
        boolean ctrHigh = ctr.compareTo(b.ctrAvg()) >= 0;

        if (roasHigh && (costLow || ctrHigh)) {
            reasonTags.add("HIGH_ROAS");
            if (costLow) {
                reasonTags.add("ROOM_FOR_GROWTH");
            }
            if (ctrHigh) {
                reasonTags.add("HIGH_CTR");
            }
            ruleHits.add(String.format("POTENTIAL: roas(%s) >= p75(%s) && (cost(%s) < p50(%s) || ctr(%s) >= avg(%s))",
                    roas, b.roasP75(), cost, b.costP50(), ctr, b.ctrAvg()));
            return true;
        }
        return false;
    }

    /**
     * 判定黑洞素材 (BLACKHOLE)
     * 条件：ROAS <= roasP25 && CPI > cpiAvg
     */
    private boolean isBlackhole(MaterialPerformance7D p, MaterialBenchmark7D b,
                                List<String> reasonTags, List<String> ruleHits) {
        BigDecimal roas = p.roas();
        BigDecimal cpi = p.cpi();

        boolean roasLow = roas.compareTo(b.roasP25()) <= 0;
        boolean cpiHigh = cpi.compareTo(b.cpiAvg()) > 0;

        if (roasLow && cpiHigh) {
            reasonTags.add("LOW_ROAS");
            reasonTags.add("HIGH_CPI");
            ruleHits.add(String.format("BLACKHOLE: roas(%s) <= p25(%s) && cpi(%s) > avg(%s)",
                    roas, b.roasP25(), cpi, b.cpiAvg()));
            return true;
        }
        return false;
    }

    /**
     * 判定风险素材 (RISK)
     * 条件：ROAS <= roasP25 || (CTR < ctrAvg && CVR < cvrInstallAvg)
     */
    private boolean isRisk(MaterialPerformance7D p, MaterialBenchmark7D b,
                           List<String> reasonTags, List<String> ruleHits) {
        BigDecimal roas = p.roas();
        BigDecimal ctr = p.ctr();
        BigDecimal cvr = p.cvrInstall();

        boolean roasLow = roas.compareTo(b.roasP25()) <= 0;
        boolean ctrLow = ctr.compareTo(b.ctrAvg()) < 0;
        boolean cvrLow = cvr.compareTo(b.cvrInstallAvg()) < 0;

        if (roasLow || (ctrLow && cvrLow)) {
            if (roasLow) {
                reasonTags.add("LOW_ROAS");
            }
            if (ctrLow && cvrLow) {
                reasonTags.add("LOW_CTR");
                reasonTags.add("LOW_CVR");
            }
            ruleHits.add(String.format("RISK: roas(%s) <= p25(%s) || (ctr(%s) < avg(%s) && cvr(%s) < avg(%s))",
                    roas, b.roasP25(), ctr, b.ctrAvg(), cvr, b.cvrInstallAvg()));
            return true;
        }
        return false;
    }

    private MaterialQualityResult buildResult(
            String tenantId, String creativeId, String materialName, LocalDate statDate,
            MaterialQualityLevel level, MaterialSampleStatus sampleStatus,
            BigDecimal score, List<String> reasonTags, List<String> ruleHits,
            String advice, MaterialPerformance7D p, MaterialBenchmark7D b) {

        String id = "mqr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new MaterialQualityResult(
                id, tenantId, creativeId, materialName, statDate,
                level, sampleStatus, score, reasonTags, ruleHits, advice, p, b, Instant.now()
        );
    }
}
