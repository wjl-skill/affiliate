package com.affiliate.platform.service.ai.dashboard;

import com.affiliate.platform.domain.ai.AiGenerateTaskRecord;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardRequest;
import com.affiliate.platform.domain.ai.dashboard.AiVideoUsageDashboardResponse;

import java.util.List;

/**
 * AI 视频使用量与算力成本大盘分析服务接口
 */
public interface AiVideoUsageDashboardService {

    /**
     * 查询指定租户与筛选条件下的算力消耗概览、趋势与多维统计
     */
    AiVideoUsageDashboardResponse getOverview(String tenantId, AiVideoUsageDashboardRequest request);

    /**
     * 分页查询生成明细列表
     */
    List<AiGenerateTaskRecord> getDetails(String tenantId, AiVideoUsageDashboardRequest request);
}
