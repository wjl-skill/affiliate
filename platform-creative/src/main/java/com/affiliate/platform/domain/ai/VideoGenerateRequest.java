package com.affiliate.platform.domain.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

/**
 * 视频生成业务请求统一输入对象 (Video Generate Request)
 */
public record VideoGenerateRequest(
        @NotBlank(message = "modelCode cannot be blank")
        String modelCode,

        @NotNull(message = "generateType cannot be null")
        AiGenerateType generateType,

        @NotBlank(message = "prompt cannot be blank")
        String prompt,

        String aspectRatio,

        @Positive(message = "durationSeconds must be positive")
        int durationSeconds,

        String startImageUrl,

        String endImageUrl,

        List<String> referenceImageUrls,

        String sourceVideoUrl,

        Map<String, Object> extParams,

        String departmentId,

        String projectId
) {
    public VideoGenerateRequest {
        aspectRatio = (aspectRatio == null || aspectRatio.isBlank()) ? "9:16" : aspectRatio;
        durationSeconds = durationSeconds > 0 ? durationSeconds : 5;
        referenceImageUrls = referenceImageUrls == null ? List.of() : List.copyOf(referenceImageUrls);
        extParams = extParams == null ? Map.of() : Map.copyOf(extParams);
    }
}
