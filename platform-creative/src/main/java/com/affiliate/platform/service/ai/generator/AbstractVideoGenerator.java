package com.affiliate.platform.service.ai.generator;

import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.service.ai.provider.AiVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.VideoProviderFactory;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitRequest;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 视频模型生成器骨架抽象基类 (Abstract Video Generator)
 * <p>
 * 提供通用的参数归一化、供应商委派调度及默认扩展属性处理。
 */
public abstract class AbstractVideoGenerator implements IVideoGenerator {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final VideoProviderFactory providerFactory;

    protected AbstractVideoGenerator(VideoProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    /**
     * 获取对应的上游模型标识 ID
     */
    protected abstract String resolveUpstreamModelId(VideoGenerateRequest request);

    /**
     * 模型特定的定制参数映射
     */
    protected Map<String, Object> customizeParameters(VideoGenerateRequest request) {
        return new HashMap<>(request.extParams());
    }

    @Override
    public ProviderSubmitResult generate(VideoGenerateRequest request) {
        log.info("[Generator:{}] Generating for request: type={}, duration={}, aspectRatio={}",
                supportModelCode(), request.generateType(), request.durationSeconds(), request.aspectRatio());

        String upstreamModelId = resolveUpstreamModelId(request);
        Map<String, Object> parameters = customizeParameters(request);

        ProviderSubmitRequest submitRequest = new ProviderSubmitRequest(
                upstreamModelId,
                request.prompt(),
                request.aspectRatio(),
                request.durationSeconds(),
                request.startImageUrl(),
                request.endImageUrl(),
                request.referenceImageUrls(),
                request.sourceVideoUrl(),
                parameters
        );

        AiVideoProviderAdapter provider = providerFactory.getProvider(providerCode());
        return provider.submit(submitRequest);
    }
}
