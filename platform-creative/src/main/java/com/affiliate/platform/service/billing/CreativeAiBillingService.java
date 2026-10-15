package com.affiliate.platform.service.billing;

import java.math.BigDecimal;

/**
 * AI 算力计费两阶段管控服务接口 (预占、实扣、释放退回)
 */
public interface CreativeAiBillingService {

    /**
     * 第一阶段：创建任务前预占算力点数
     */
    boolean reserveComputeFund(String tenantId, String taskNo, BigDecimal estimatedAmount);

    /**
     * 第二阶段：任务成功时原子实扣并生成不可变账单分录
     */
    boolean confirmComputeDeduction(String tenantId, String taskNo, BigDecimal actualAmount, String departmentId, String projectId, String modelCode);

    /**
     * 异常回滚：任务失败或被用户取消时立即全额释放解冻点数
     */
    boolean releaseComputeHold(String tenantId, String taskNo);

    /**
     * 查询指定任务当前是否存在未决冻结预占
     */
    boolean hasActiveHold(String taskNo);

    /**
     * 获取指定租户算力扣减账单分录列表
     */
    java.util.List<com.affiliate.platform.entity.CreativeAiBillingLedgerEntity> listLedgers(String tenantId);
}
