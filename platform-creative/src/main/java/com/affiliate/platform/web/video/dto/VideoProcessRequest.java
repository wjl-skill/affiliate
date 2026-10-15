package com.affiliate.platform.web.video.dto;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.VideoProcessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 视频处理提交请求
 */
public record VideoProcessRequest(
        @NotNull(message = "processType cannot be null")
        VideoProcessType processType,

        @NotBlank(message = "sourceVideoUrl cannot be blank")
        String sourceVideoUrl,

        String introVideoUrl,
        Integer targetWidth,
        Integer targetHeight,
        AlignMode alignMode,
        Map<String, Object> parameters
) {}
