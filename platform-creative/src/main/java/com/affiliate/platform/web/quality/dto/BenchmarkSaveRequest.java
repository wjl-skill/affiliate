package com.affiliate.platform.web.quality.dto;

import com.affiliate.platform.domain.quality.MaterialBenchmark7D;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 7日大盘质量基准线保存请求 DTO
 */
public record BenchmarkSaveRequest(
        @NotNull(message = "statDate cannot be null")
        LocalDate statDate,

        LocalDate windowStartDate,

        LocalDate windowEndDate,

        @PositiveOrZero
        int materialSampleCount,

        @Positive
        BigDecimal sampleCostThreshold,

        @Positive
        long sampleClickThreshold,

        long sampleInstallThreshold,

        @Positive
        BigDecimal roasP75,

        @Positive
        BigDecimal roasP25,

        @Positive
        BigDecimal costP50,

        @Positive
        BigDecimal cpiAvg,

        @Positive
        BigDecimal ctrAvg,

        @Positive
        BigDecimal cvrInstallAvg
) {
    public MaterialBenchmark7D toDomain(String tenantId) {
        LocalDate end = statDate != null ? statDate : LocalDate.now();
        LocalDate start = windowStartDate != null ? windowStartDate : end.minusDays(6);
        return new MaterialBenchmark7D(
                "bm_" + end.toString().replace("-", ""),
                tenantId != null ? tenantId : "public",
                end,
                start,
                windowEndDate != null ? windowEndDate : end,
                materialSampleCount,
                sampleCostThreshold,
                sampleClickThreshold,
                sampleInstallThreshold,
                roasP75,
                roasP25,
                costP50,
                cpiAvg,
                ctrAvg,
                cvrInstallAvg,
                Instant.now()
        );
    }
}
