package com.affiliate.platform.service.ai.generator.impl;

import com.affiliate.platform.domain.ai.AiGenerateType;
import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.service.ai.generator.AbstractVideoGenerator;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 字节跳动火山引擎 Seedance 2.0 视频生成器
 */
@Component
public class ArkSeedanceVideoGenerator extends AbstractVideoGenerator {

    public static final String MODEL_CODE = "ark_seedance_2_0";
    public static final String PROVIDER_CODE = "BYTEDANCE_ARK";

    public ArkSeedanceVideoGenerator(VideoProviderFactory providerFactory) {
        super(providerFactory);
    }

    @Override
    public String supportModelCode() {
        return MODEL_CODE;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    protected String resolveUpstreamModelId(VideoGenerateRequest request) {
        if (request.generateType() == AiGenerateType.IMAGE_TO_VIDEO) {
            return "seedance-2.0-i2v";
        }
        return "seedance-2.0-pro";
    }

    @Override
    protected Map<String, Object> customizeParameters(VideoGenerateRequest request) {
        Map<String, Object> params = super.customizeParameters(request);
        // 自动注入边界关键帧支持与动作控制参数
        if (request.endImageUrl() != null && !request.endImageUrl().isBlank()) {
            params.put("end_frame_control", true);
        }
        if (!params.containsKey("motion_strength")) {
            params.put("motion_strength", "medium");
        }
        return params;
    }
}
