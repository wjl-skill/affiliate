package com.affiliate.platform.web.video.dto;

import java.util.List;

/**
 * 批量混剪触发执行结果
 */
public record AutoStitchTriggerResult(
        String configId,
        int generatedTaskCount,
        List<String> taskNos
) {}
