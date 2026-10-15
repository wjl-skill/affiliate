package com.affiliate.platform.service.ad;

import com.affiliate.platform.domain.quality.MaterialQualityLevel;

import java.math.BigDecimal;

/**
 * 素材质量标签赋能 DSP 竞价引擎与 Affiliate SmartLink 路由服务接口
 */
public interface CreativeDspLinkageService {

    /**
     * 获取 DSP 智能出价算法质量增益系数 QualityMultiplier
     */
    double getDspQualityMultiplier(MaterialQualityLevel level);

    /**
     * 判断该素材是否允许参与 DSP 实时竞价 (黑洞素材自动剔除)
     */
    boolean isEligibleForDspBidding(MaterialQualityLevel level);

    /**
     * 计算 Affiliate 网盟 SmartLink / TDS 分流路由综合得分
     */
    BigDecimal calculateSmartLinkRoutingScore(BigDecimal epc, double capFactor, MaterialQualityLevel level);
}
