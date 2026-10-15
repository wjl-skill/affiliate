package com.affiliate.platform.service.ai.generator.impl;

import com.affiliate.platform.domain.ai.AiGenerateType;
import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.service.ai.generator.AbstractVideoGenerator;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阿里百炼 Wan 2.1 (通义万相) 视频生成器
 */
@Component
public class DashScopeWanVideoGenerator extends AbstractVideoGenerator {

    public static final String MODEL_CODE = "dashscope_wan_2_1";
    public static final String PROVIDER_CODE = "DASHSCOPE";

    public DashScopeWanVideoGenerator(VideoProviderFactory providerFactory) {
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
            return "wan2.1-i2v-plus";
        }
        return "wan2.1-t2v-plus";
    }

    @Override
    protected Map<String, Object> customizeParameters(VideoGenerateRequest request) {
        Map<String, Object> params = super.customizeParameters(request);
        params.put("wan_watermark", false); // 默认去除官方测试水印
        return params;
    }
}
