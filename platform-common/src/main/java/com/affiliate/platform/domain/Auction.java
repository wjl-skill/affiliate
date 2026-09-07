package com.affiliate.platform.domain;

import com.affiliate.platform.service.DomainValidationException;

import java.time.Instant;

/**
 * 竞价拍卖成交事实记录实体 (Auction Domain Record)
 * <p>
 * 记录实时竞价（RTB）撮合环节产生的出价与成交快照，作为后续计费分录生成与多维报表统计的核心事实源。
 *
 * @param id            拍卖流水唯一主键 ID
 * @param requestId     OpenRTB 竞价请求全局唯一追踪 ID
 * @param adSlotId      中标的媒体广告位 ID
 * @param creativeId    中标展示的广告素材 ID
 * @param clearingPrice 最终结算成交价格（不可为负数）
 * @param currency      结算货币三位国际代码（如 "USD", "CNY"）
 * @param advertiser    中标广告主或广告计划标识
 * @param createdAt     竞价成交时间戳
 */
public record Auction(
        String id,
        String requestId,
        String adSlotId,
        String creativeId,
        double clearingPrice,
        String currency,
        String advertiser,
        Instant createdAt
) {
    /**
     * 紧凑构造器 - 校验成交价格非负与货币代码大写规整
     */
    public Auction {
        // 校验结算成交价格非负
        if (clearingPrice < 0) throw new DomainValidationException("clearingPrice", "cannot be negative");
        // 默认货币为 USD，自动转为标准大写格式
        currency = (currency == null || currency.isBlank()) ? "USD" : currency.toUpperCase();
    }
}
