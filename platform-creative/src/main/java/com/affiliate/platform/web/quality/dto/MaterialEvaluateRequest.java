package com.affiliate.platform.web.quality.dto;

import com.affiliate.platform.domain.quality.MaterialPerformance7D;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 素材投放效果评定请求 DTO
 */
public record MaterialEvaluateRequest(
        @NotBlank(message = "creativeId cannot be blank")
        String creativeId,

        String materialName,

        String adName,

        LocalDate statDate,

        @PositiveOrZero(message = "promotionCost must be positive or zero")
        BigDecimal promotionCost,

        @PositiveOrZero(message = "impressions must be positive or zero")
        long impressions,

        @PositiveOrZero(message = "clicks must be positive or zero")
        long clicks,

        @PositiveOrZero(message = "installNum must be positive or zero")
        long installNum,

        @PositiveOrZero(message = "conversionValues must be positive or zero")
        BigDecimal conversionValues
) {
    public MaterialPerformance7D toDomain() {
        return new MaterialPerformance7D(
                creativeId,
                creativeId,
                materialName != null ? materialName : "Material_" + creativeId,
                adName != null ? adName : materialName,
                statDate != null ? statDate : LocalDate.now(),
                promotionCost != null ? promotionCost : BigDecimal.ZERO,
                impressions,
                clicks,
                installNum,
                conversionValues != null ? conversionValues : BigDecimal.ZERO
        );
    }
}
