package com.affiliate.platform.budget;

import java.math.BigDecimal;

/**
 * 广告预算管控与原子预占服务通用契约 (Budget Management Service Interface)
 * <p>
 * 专为高并发 RTB 交易场景设计：
 * 1. 竞价前执行资金原子预占 (reserve)，阻断超卖；
 * 2. 竞价胜出确认转正扣款 (confirm)；
 * 3. 竞价未胜出或异常释放归还资金 (release)。
 */
public interface BudgetService {

    /**
     * 竞价前原子预占预算 (Budget Reservation)
     *
     * @param tenantId   租户标识
     * @param campaignId 广告活动标识
     * @param userId     用户/设备标识
     * @param amount     本次预占扣除金额
     * @return 包含唯一标识的预占凭证 Reservation 对象
     */
    Reservation reserve(String tenantId, String campaignId, String userId, BigDecimal amount);

    /**
     * 胜出通知确认：转正预占资金并销毁预占临时记录
     *
     * @param reservation 预占凭证
     */
    void confirm(Reservation reservation);

    /**
     * 竞价未胜出释放：将预占资金原路返还至主预算池
     *
     * @param reservation 预占凭证
     */
    void release(Reservation reservation);

    /**
     * 设置/刷新活动初始主预算
     *
     * @param tenantId   租户标识
     * @param campaignId 活动标识
     * @param amount     预算金额
     */
    default void setBudget(String tenantId, String campaignId, BigDecimal amount) {}

    /**
     * 预算原子预占临时凭据实体 (Budget Reservation Record)
     *
     * @param id         预占流水唯一主键 ID
     * @param tenantId   租户标识
     * @param campaignId 广告活动标识
     * @param userId     用户标识
     * @param amount     预占金额
     */
    record Reservation(String id, String tenantId, String campaignId, String userId, BigDecimal amount) {}
}
