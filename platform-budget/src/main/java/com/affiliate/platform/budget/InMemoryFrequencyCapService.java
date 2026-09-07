package com.affiliate.platform.budget;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于本地 JVM 内存双端队列的滑动窗口频控实现 (In-Memory Frequency Cap Service)
 * <p>
 * 在未开启外部 Redis 中间件时作为默认回退实现，使用 ConcurrentLinkedDeque 记录滑动窗口内的时间戳。
 */
@Service
@ConditionalOnProperty(name = "app.infrastructure.redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryFrequencyCapService implements FrequencyCapService {

    // 内存存储容器：Key 为 tenant:campaign:userId，Value 为曝光时间戳双端队列
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Instant>> records = new ConcurrentHashMap<>();

    /**
     * 检查并原子记录单次用户曝光
     *
     * @param tenantId   租户标识
     * @param campaignId 广告活动标识
     * @param userId     用户标识
     * @param limit      窗口期内最大允许次数
     * @param window     滑动时间窗口时长
     * @return true: 允许曝光；false: 超限拦截
     */
    @Override
    public boolean checkAndIncrement(String tenantId, String campaignId, String userId, int limit, Duration window) {
        if (userId == null || userId.isBlank() || limit <= 0) {
            return true;
        }
        String key = tenantId + ":" + campaignId + ":" + userId;
        ConcurrentLinkedDeque<Instant> deque = records.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        // 使用同步块确保单个用户维度的淘汰与插入原子性
        synchronized (deque) {
            // 移除窗口左侧已过期的历史曝光戳
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            // 判断当前窗口内剩余曝光数是否超限
            if (deque.size() >= limit) {
                return false; // 已达到上限，拒绝曝光
            }
            // 记录本次曝光时间戳
            deque.addLast(now);
            return true;
        }
    }

    /**
     * 重置指定用户的内存频控队列
     *
     * @param tenantId   租户标识
     * @param campaignId 活动标识
     * @param userId     用户标识
     */
    @Override
    public void reset(String tenantId, String campaignId, String userId) {
        records.remove(tenantId + ":" + campaignId + ":" + userId);
    }
}
