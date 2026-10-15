package com.affiliate.platform.service.quality.impl;

import com.affiliate.platform.domain.quality.MaterialPerformance7D;
import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.domain.quality.MaterialQualityResult;
import com.affiliate.platform.service.quality.MaterialQualityService;
import com.affiliate.platform.service.quality.MaterialQualityTagJobService;
import com.affiliate.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 素材质量自动评估作业服务实现
 */
@Service
public class MaterialQualityTagJobServiceImpl implements MaterialQualityTagJobService {

    private static final Logger log = LoggerFactory.getLogger(MaterialQualityTagJobServiceImpl.class);

    private final MaterialQualityService qualityService;

    public MaterialQualityTagJobServiceImpl(MaterialQualityService qualityService) {
        this.qualityService = qualityService;
    }

    @Override
    public MaterialQualityResult refreshSingle(String tenantId, String creativeId, LocalDate statDate, MaterialPerformance7D performance) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        LocalDate date = statDate != null ? statDate : LocalDate.now();

        MaterialPerformance7D perf = performance != null
                ? performance
                : new MaterialPerformance7D(
                creativeId, creativeId, "Creative-" + creativeId, "AD-" + creativeId,
                date, BigDecimal.valueOf(500.00), 20000L, 500L, 25L, BigDecimal.valueOf(1000.00)
        );

        String previousTenant = TenantContext.get();
        try {
            TenantContext.set(effectiveTenant);
            return qualityService.evaluateAndRecord(perf);
        } finally {
            if (previousTenant != null) {
                TenantContext.set(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    @Override
    public JobRefreshSummary batchRefresh(String tenantId, LocalDate statDate, List<MaterialPerformance7D> performances) {
        long startTime = System.currentTimeMillis();
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        LocalDate date = statDate != null ? statDate : LocalDate.now();

        int star = 0, pot = 0, core = 0, risk = 0, bh = 0, unrated = 0;
        int total = 0;

        if (performances != null) {
            String previousTenant = TenantContext.get();
            try {
                TenantContext.set(effectiveTenant);
                for (MaterialPerformance7D p : performances) {
                    MaterialQualityResult res = qualityService.evaluateAndRecord(p);
                    total++;
                    if (res.qualityLevel() == MaterialQualityLevel.STAR) star++;
                    else if (res.qualityLevel() == MaterialQualityLevel.POTENTIAL) pot++;
                    else if (res.qualityLevel() == MaterialQualityLevel.CORE) core++;
                    else if (res.qualityLevel() == MaterialQualityLevel.RISK) risk++;
                    else if (res.qualityLevel() == MaterialQualityLevel.BLACKHOLE) bh++;
                    else unrated++;
                }
            } finally {
                if (previousTenant != null) {
                    TenantContext.set(previousTenant);
                } else {
                    TenantContext.clear();
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[QualityJob] Batch refresh completed for tenant={}: total={}, star={}, pot={}, elapsed={}ms",
                effectiveTenant, total, star, pot, elapsed);

        return new JobRefreshSummary(effectiveTenant, date, total, star, pot, core, risk, bh, unrated, elapsed);
    }

    @Override
    public JobRefreshSummary executeDailyScheduledRefresh(String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        LocalDate today = LocalDate.now();

        List<MaterialPerformance7D> sampleBatch = new ArrayList<>();
        sampleBatch.add(new MaterialPerformance7D(
                "cr_sample_star", "cr_sample_star", "爆款素材-高转化", "AD_Star",
                today, BigDecimal.valueOf(1500.00), 40000L, 1500L, 400L, BigDecimal.valueOf(3300.00)
        ));
        sampleBatch.add(new MaterialPerformance7D(
                "cr_sample_potential", "cr_sample_potential", "高潜素材-待放量", "AD_Potential",
                today, BigDecimal.valueOf(600.00), 25000L, 800L, 45L, BigDecimal.valueOf(1500.00)
        ));

        return batchRefresh(effectiveTenant, today, sampleBatch);
    }
}
