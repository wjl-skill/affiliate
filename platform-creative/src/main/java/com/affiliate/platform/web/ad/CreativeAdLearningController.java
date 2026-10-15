package com.affiliate.platform.web.ad;

import com.affiliate.platform.service.ad.AdLearningRuleEngine;
import com.affiliate.platform.service.ad.AdLearningStatus;
import com.affiliate.platform.service.ad.CreativeDspLinkageService;
import com.affiliate.platform.web.ad.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 广告学习期与 DSP 联动 REST 控制器
 */
@RestController
@RequestMapping("/api/v1/creative/ad")
public class CreativeAdLearningController {

    private final AdLearningRuleEngine learningRuleEngine;
    private final CreativeDspLinkageService dspLinkageService;

    public CreativeAdLearningController(
            AdLearningRuleEngine learningRuleEngine,
            CreativeDspLinkageService dspLinkageService
    ) {
        this.learningRuleEngine = learningRuleEngine;
        this.dspLinkageService = dspLinkageService;
    }

    /**
     * 判定素材当前广告学习期状态与预算调整限制
     * POST /api/v1/creative/ad/learning-status
     */
    @PostMapping("/learning-status")
    public AdLearningStatus evaluateLearningStatus(@Valid @RequestBody AdLearningEvaluationRequest request) {
        return learningRuleEngine.evaluateLearningPhase(
                request.firstMetricTime(),
                request.cumulativeCost(),
                request.qualityLevel(),
                request.timeDaysThreshold(),
                request.costThreshold()
        );
    }

    /**
     * 查询 DSP 智能出价算法质量增益系数与参竞资格
     * POST /api/v1/creative/ad/dsp-multiplier
     */
    @PostMapping("/dsp-multiplier")
    public DspMultiplierResponse getDspMultiplier(@Valid @RequestBody DspMultiplierRequest request) {
        double multiplier = dspLinkageService.getDspQualityMultiplier(request.qualityLevel());
        boolean eligible = dspLinkageService.isEligibleForDspBidding(request.qualityLevel());
        return new DspMultiplierResponse(request.qualityLevel(), multiplier, eligible);
    }

    /**
     * 计算 Affiliate 网盟 SmartLink / TDS 动态分流路由综合得分
     * POST /api/v1/creative/ad/smart-link-score
     */
    @PostMapping("/smart-link-score")
    public SmartLinkScoreResponse calculateSmartLinkScore(@Valid @RequestBody SmartLinkScoreRequest request) {
        BigDecimal score = dspLinkageService.calculateSmartLinkRoutingScore(
                request.epc(),
                request.capFactor(),
                request.qualityLevel()
        );
        return new SmartLinkScoreResponse(request.epc(), request.capFactor(), request.qualityLevel(), score);
    }
}
