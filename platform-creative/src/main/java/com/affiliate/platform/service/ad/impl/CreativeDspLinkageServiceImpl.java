package com.affiliate.platform.service.ad.impl;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;
import com.affiliate.platform.service.ad.CreativeDspLinkageService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 素材质量赋能 DSP 与 SmartLink 联动实现
 */
@Service
public class CreativeDspLinkageServiceImpl implements CreativeDspLinkageService {

    @Override
    public double getDspQualityMultiplier(MaterialQualityLevel level) {
        if (level == null) {
            return 1.0;
        }
        return switch (level) {
            case STAR -> 1.25;       // 明星素材：加权 25%，降低 Bid Shading 阻尼折扣，高价抢顶流曝光
            case POTENTIAL -> 1.10;  // 潜力素材：适度放量加权 10%
            case CORE -> 1.00;       // 主力素材：标准出价系数
            case RISK -> 0.50;       // 风险素材：出价腰斩降权 50%
            case BLACKHOLE -> 0.00;  // 黑洞素材：禁止出价
            case UNRATED -> 1.00;    // 未评级：平平无奇标准出价
        };
    }

    @Override
    public boolean isEligibleForDspBidding(MaterialQualityLevel level) {
        return level != MaterialQualityLevel.BLACKHOLE;
    }

    @Override
    public BigDecimal calculateSmartLinkRoutingScore(BigDecimal epc, double capFactor, MaterialQualityLevel level) {
        BigDecimal baseEpc = epc != null ? epc : BigDecimal.ZERO;
        double safeCap = Math.max(0.0, capFactor);

        // 明星素材额外获得 30% 路由权重加成
        double qualityBoost = (level == MaterialQualityLevel.STAR) ? 1.30 : 1.00;

        return baseEpc.multiply(BigDecimal.valueOf(safeCap * qualityBoost))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
