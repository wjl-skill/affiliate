package com.affiliate.platform.budget;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 预算阶梯消耗告警与防超卖熔断服务 (Budget Threshold Alert Service)
 * <p>
 * 实时监控活动的累计消耗百分比：
 * 分别在消耗达到 50%、80%、90%、100% 时触发告警事件并做单日去重，避免重复告警风暴。
 */
@Service
public class BudgetAlertService {

    private final EventPublisher events;

    // 状态记录：Key 为 "tenantId:campaignId"，Value 为已触发告警的最高百分比水位 (如 80)
    private final ConcurrentMap<String, Integer> highestAlertLevel = new ConcurrentHashMap<>();

    public BudgetAlertService(EventPublisher events) {
        this.events = events != null ? events : e -> {};
    }

    /**
     * 告警检查与事件触发
     *
     * @param tenantId     租户标识
     * @param campaignId   活动标识
     * @param currentSpend 当前消耗金额
     * @param dailyBudget  每日预算限额
     * @return 当前触发的新告警水位（0 代表无新告警，50/80/90/100 代表对应水位）
     */
    public int checkAndAlert(String tenantId, String campaignId, BigDecimal currentSpend, BigDecimal dailyBudget) {
        if (dailyBudget == null || dailyBudget.signum() <= 0 || currentSpend == null) {
            return 0;
        }

        double ratio = currentSpend.divide(dailyBudget, 4, RoundingMode.HALF_UP).doubleValue();
        int currentLevel = 0;
        if (ratio >= 1.0) {
            currentLevel = 100;
        } else if (ratio >= 0.90) {
            currentLevel = 90;
        } else if (ratio >= 0.80) {
            currentLevel = 80;
        } else if (ratio >= 0.50) {
            currentLevel = 50;
        }

        if (currentLevel == 0) {
            return 0;
        }

        String key = tenantId + ":" + campaignId;
        int previousLevel = highestAlertLevel.getOrDefault(key, 0);

        if (currentLevel > previousLevel) {
            highestAlertLevel.put(key, currentLevel);
            events.publish(DomainEvent.create(
                    "budget.threshold_reached.v1",
                    tenantId,
                    campaignId,
                    Map.of(
                            "level", currentLevel,
                            "currentSpend", currentSpend,
                            "dailyBudget", dailyBudget,
                            "exhausted", currentLevel == 100
                    )
            ));
            return currentLevel;
        }

        return 0;
    }

    /**
     * 重置指定活动告警状态（用于次日 0 点预算轮转重置）
     */
    public void reset(String tenantId, String campaignId) {
        highestAlertLevel.remove(tenantId + ":" + campaignId);
    }
}
