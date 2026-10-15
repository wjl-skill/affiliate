package com.affiliate.platform.domain.ai.dashboard;

import java.io.Serializable;
import java.util.List;

/**
 * AI 视频使用量与算力成本大盘查询请求
 */
public record AiVideoUsageDashboardRequest(
        String startDate,
        String endDate,
        List<String> modelCodes,
        List<String> taskStatuses,
        String departmentId,
        String createdBy,
        Integer pageNum,
        Integer pageSize
) implements Serializable {

    public AiVideoUsageDashboardRequest {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        } else if (pageSize > 100) {
            pageSize = 100;
        }
    }

    public static AiVideoUsageDashboardRequest all() {
        return new AiVideoUsageDashboardRequest(null, null, null, null, null, null, 1, 20);
    }
}
