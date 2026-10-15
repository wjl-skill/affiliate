package com.affiliate.platform.service.ai.generator;

import com.affiliate.platform.domain.ai.VideoGenerateRequest;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitResult;

/**
 * 模型业务语义生成器策略接口 (Video Generator Strategy Interface)
 * <p>
 * 负责特定模型的业务参数映射、提示词组装与入参归一化。
 */
public interface IVideoGenerator {

    /**
     * 该生成器支持的统一模型编码 (如 "ark_seedance_2_0", "dashscope_wan_2_1")
     */
    String supportModelCode();

    /**
     * 该模型背后依赖的供应商编码 (如 "BYTEDANCE_ARK", "DASHSCOPE")
     */
    String providerCode();

    /**
     * 执行业务参数向底层厂商请求的映射并提交生成
     */
    ProviderSubmitResult generate(VideoGenerateRequest request);
}
