package com.affiliate.platform.service.ai.generator.impl;

import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.service.ai.generator.AbstractVideoGenerator;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * HappyHorse 1.1 视频编辑与指令重绘生成器
 */
@Component
public class HappyHorseVideoGenerator extends AbstractVideoGenerator {

    public static final String MODEL_CODE = "happyhorse_1_1";
    public static final String PROVIDER_CODE = "DASHSCOPE";

    public HappyHorseVideoGenerator(VideoProviderFactory providerFactory) {
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
        return "happyhorse-v1.1-edit";
    }

    @Override
    protected Map<String, Object> customizeParameters(VideoGenerateRequest request) {
        Map<String, Object> params = super.customizeParameters(request);
        if (request.sourceVideoUrl() != null) {
            params.put("video_url", request.sourceVideoUrl());
        }
        return params;
    }
}
