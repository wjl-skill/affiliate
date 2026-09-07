package com.affiliate.platform.cdp;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 客户价值 RFM 计算模型服务 (CDP RFM Scoring Service)
 * <p>
 * 依据工业界经典的 RFM 用户价值模型：
 * 1. Recency (R)：最近一次消费间隔天数；
 * 2. Frequency (F)：历史累计消费频次；
 * 3. Monetary (M)：历史累计消费总金额；
 * 将客户智能归类为：核心冠军客户 (CHAMPION)、忠诚客户 (LOYAL)、流失预警 (AT_RISK)、新晋客户 (NEW_CUSTOMER)。
 */
@Service
public class RfmScoringService {

    public enum CustomerTier {
        /** 核心冠军高净值客户 */
        CHAMPION,
        /** 忠诚稳定客户 */
        LOYAL,
        /** 流失预警召回客户 */
        AT_RISK,
        /** 新晋首单客户 */
        NEW_CUSTOMER,
        /** 普通潜力客户 */
        POTENTIAL
    }

    public record RfmScore(
            long recencyDays,
            int frequency,
            BigDecimal monetary,
            CustomerTier tier
    ) {}

    /**
     * 根据客户历史购买事件计算 RFM 分值与客户等级
     */
    public RfmScore calculate(List<CustomerTimelineService.CustomerEvent> events, Instant referenceTime) {
        if (events == null || events.isEmpty()) {
            return new RfmScore(999, 0, BigDecimal.ZERO, CustomerTier.POTENTIAL);
        }

        Instant ref = referenceTime == null ? Instant.now() : referenceTime;
        Instant latestPurchase = null;
        int frequency = 0;
        BigDecimal totalMonetary = BigDecimal.ZERO;

        for (CustomerTimelineService.CustomerEvent evt : events) {
            if (evt.type() == CustomerTimelineService.EventType.PURCHASE) {
                frequency++;
                if (latestPurchase == null || evt.timestamp().isAfter(latestPurchase)) {
                    latestPurchase = evt.timestamp();
                }
                String amountStr = evt.payload().get("amount");
                if (amountStr != null) {
                    try {
                        totalMonetary = totalMonetary.add(new BigDecimal(amountStr));
                    } catch (Exception ignored) {}
                }
            }
        }

        if (latestPurchase == null) {
            return new RfmScore(999, 0, BigDecimal.ZERO, CustomerTier.POTENTIAL);
        }

        long recencyDays = Math.max(0, Duration.between(latestPurchase, ref).toDays());

        // 判定客户层级
        CustomerTier tier;
        if (recencyDays <= 30 && frequency >= 3 && totalMonetary.compareTo(new BigDecimal("500")) >= 0) {
            tier = CustomerTier.CHAMPION;
        } else if (recencyDays <= 90 && frequency >= 2 && totalMonetary.compareTo(new BigDecimal("200")) >= 0) {
            tier = CustomerTier.LOYAL;
        } else if (recencyDays > 90 && frequency >= 2 && totalMonetary.compareTo(new BigDecimal("200")) >= 0) {
            tier = CustomerTier.AT_RISK;
        } else if (recencyDays <= 30 && frequency == 1) {
            tier = CustomerTier.NEW_CUSTOMER;
        } else {
            tier = CustomerTier.POTENTIAL;
        }

        return new RfmScore(recencyDays, frequency, totalMonetary, tier);
    }
}
