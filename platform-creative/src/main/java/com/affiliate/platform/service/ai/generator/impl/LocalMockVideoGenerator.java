package com.affiliate.platform.service.ai.generator.impl;

import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.service.ai.generator.AbstractVideoGenerator;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import org.springframework.stereotype.Component;

/**
 * 本地 Mock 极速视频生成器
 */
@Component
public class LocalMockVideoGenerator extends AbstractVideoGenerator {

    public static final String MODEL_CODE = "local_mock_video";
    public static final String PROVIDER_CODE = "LOCAL_MOCK";

    public LocalMockVideoGenerator(VideoProviderFactory providerFactory) {
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
        return "mock-fast-v1";
    }
}
