package com.affiliate.platform.service.ai.provider.dto;

import java.util.List;
import java.util.Map;

/**
 * 供应商底层提交任务协议请求 DTO (Provider Submit Request)
 */
public record ProviderSubmitRequest(
        String upstreamModelId,
        String prompt,
        String aspectRatio,
        int durationSeconds,
        String startImageUrl,
        String endImageUrl,
        List<String> referenceImageUrls,
        String sourceVideoUrl,
        Map<String, Object> parameters
) {
    public ProviderSubmitRequest {
        referenceImageUrls = referenceImageUrls == null ? List.of() : List.copyOf(referenceImageUrls);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
