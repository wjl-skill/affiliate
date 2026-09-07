package com.affiliate.platform.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 金融级计费记账服务通用契约接口 (Billing Service Contract)
 * <p>
 * 采用不可变复式记账账本设计 (Double-Entry Bookkeeping Ledger)：
 * 确保每笔广告扣费、媒体收益、平台佣金与退款流水具备严格的借贷方向与幂等防重保障。
 */
public interface BillingService {

    /**
     * 写入并持久化一笔计费分录
     *
     * @param entry 待入账的计费分录对象
     * @return 实际持久化或历史已存在的不可变分录
     */
    BillingEntry record(BillingEntry entry);

    /**
     * 按租户查询历史计费分录流水列表
     *
     * @param tenantId 租户标识
     * @return 分录流水列表
     */
    List<BillingEntry> list(String tenantId);

    /**
     * 不可变计费流水明细实体 (Immutable Billing Ledger Entry)
     *
     * @param id             分录全局唯一标识
     * @param tenantId       租户标识
     * @param accountId      关联账户 ID（如广告主账户或媒体账户）
     * @param auctionId      关联拍卖流水 ID（可选）
     * @param type           分录业务类型 (ADVERTISER_CHARGE, PUBLISHER_REVENUE, PLATFORM_FEE, REFUND)
     * @param direction      借贷方向 (DEBIT 借记, CREDIT 贷记)
     * @param amount         结算金额
     * @param currency       货币代码（如 USD）
     * @param idempotencyKey 全局唯一幂等防重键（保证精确一次扣款）
     * @param description    业务交易文字摘要说明
     * @param occurredAt     交易发生时间戳
     */
    record BillingEntry(
            String id,
            String tenantId,
            String accountId,
            String auctionId,
            EntryType type,
            EntryDirection direction,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String description,
            Instant occurredAt
    ) {
        /**
         * 兼容性构造器（默认借记 DEBIT，描述为空）
         */
        public BillingEntry(
                String id,
                String tenantId,
                String accountId,
                String auctionId,
                EntryType type,
                BigDecimal amount,
                String currency,
                String idempotencyKey,
                Instant occurredAt
        ) {
            this(id, tenantId, accountId, auctionId, type, EntryDirection.DEBIT, amount, currency, idempotencyKey, null, occurredAt);
        }
    }

    /**
     * 计费分录业务类型枚举
     */
    enum EntryType {
        /** 广告主出资扣费 */
        ADVERTISER_CHARGE,
        /** 媒体发布商流量收益 */
        PUBLISHER_REVENUE,
        /** 交易平台服务抽成佣金 */
        PLATFORM_FEE,
        /** 撤销或纠错退款 */
        REFUND
    }

    /**
     * 复式记账借贷方向
     */
    enum EntryDirection {
        /** 借方 (Debit) */
        DEBIT,
        /** 贷方 (Credit) */
        CREDIT
    }
}
