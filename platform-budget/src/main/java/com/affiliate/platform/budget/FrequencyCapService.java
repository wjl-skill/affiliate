package com.affiliate.platform.budget;

import java.time.Duration;

/**
 * 广告曝光频次控制通用服务契约 (Frequency Cap Service Interface)
 * <p>
 * 限制单一受众用户在指定滑动时间窗口内的最大曝光次数，杜绝过度打扰并提高广告受众覆盖率。
 */
public interface FrequencyCapService {

    /**
     * 校验本次曝光是否符合频控限制；若未超限则原子计数加 1 并返回 true
     *
     * @param tenantId   租户标识
     * @param campaignId 活动标识
     * @param userId     用户/设备唯一标识
     * @param limit      窗口期内最大允许次数
     * @param window     滑动时间窗口时长（例如 1小时、24小时）
     * @return true: 未超限允许曝光且已递增；false: 超限拦截
     */
    boolean checkAndIncrement(String tenantId, String campaignId, String userId, int limit, Duration window);

    /**
     * 重置清除指定用户的频控曝光记录
     *
     * @param tenantId   租户标识
     * @param campaignId 活动标识
     * @param userId     用户标识
     */
    void reset(String tenantId, String campaignId, String userId);
}
