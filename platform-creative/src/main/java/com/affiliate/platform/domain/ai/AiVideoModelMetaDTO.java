package com.affiliate.platform.domain.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 视频大模型元数据 DTO (Model Meta Data Transfer Object)
 * <p>
 * 驱动前端动态渲染模型选项、分辨率、比例、时长及控制参数。
 *
 * @param modelCode         模型业务编码 (如 "ark_seedance_2_0", "dashscope_wan_2_1")
 * @param modelName         模型展示名称
 * @param providerCode      所属供应商编码 (如 "BYTEDANCE_ARK", "DASHSCOPE", "OPENROUTER")
 * @param upstreamModelId   上游厂商 API 模型标识
 * @param supportedTypes    支持的生成类型列表 (T2V, I2V, MultiRef2V, VideoEdit)
 * @param billingUnit       计费单位 ("SECOND", "TOKEN")
 * @param unitPrice         单价 (元/秒 或 元/千Token)
 * @param pricingMultiplier 计费浮动倍率
 * @param enabled           是否启用
 * @param fields            动态渲染参数字段列表
 */
public record AiVideoModelMetaDTO(
        String modelCode,
        String modelName,
        String providerCode,
        String upstreamModelId,
        List<AiGenerateType> supportedTypes,
        String billingUnit,
        BigDecimal unitPrice,
        BigDecimal pricingMultiplier,
        boolean enabled,
        List<AiVideoModelFieldDTO> fields
) {
    public AiVideoModelMetaDTO {
        supportedTypes = supportedTypes == null ? List.of() : List.copyOf(supportedTypes);
        fields = fields == null ? List.of() : List.copyOf(fields);
        unitPrice = unitPrice != null ? unitPrice : BigDecimal.valueOf(0.1500);
        pricingMultiplier = pricingMultiplier != null ? pricingMultiplier : BigDecimal.ONE;
        billingUnit = billingUnit != null ? billingUnit : "SECOND";
    }

    public boolean supports(AiGenerateType type) {
        return supportedTypes.contains(type);
    }
}
