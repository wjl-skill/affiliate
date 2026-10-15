package com.affiliate.platform.repository.ai;

import com.affiliate.platform.domain.ai.AiGenerateTaskRecord;
import com.affiliate.platform.domain.ai.AiTaskStatus;

import java.util.List;
import java.util.Optional;

/**
 * AI 生成任务仓储端口 (Task Record Repository Port)
 */
public interface AiGenerateTaskRecordRepository {

    /**
     * 保存或更新任务记录
     */
    AiGenerateTaskRecord save(AiGenerateTaskRecord record);

    /**
     * 根据主键 ID 查询
     */
    Optional<AiGenerateTaskRecord> findById(String id);

    /**
     * 根据任务单号 taskNo 查询
     */
    Optional<AiGenerateTaskRecord> findByTaskNo(String tenantId, String taskNo);

    /**
     * 查询指定状态的任务列表 (供后台轮询 Worker 调度)
     */
    List<AiGenerateTaskRecord> findByStatus(AiTaskStatus status, int limit);

    /**
     * 根据租户查询最近任务列表
     */
    List<AiGenerateTaskRecord> findRecentByTenant(String tenantId, int limit);
}
