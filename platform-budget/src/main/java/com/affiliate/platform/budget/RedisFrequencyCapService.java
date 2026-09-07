package com.affiliate.platform.budget;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 基于 Redis 分布式有序集合 (ZSET) 与 Lua 脚本的高并发滑动窗口频控服务 (Redis Frequency Cap Service)
 * <p>
 * 在广告交易撮合链路中，限制特定用户/设备在指定时间窗口内（如 1 小时内限频 3 次）的最大曝光频次，杜绝广告骚扰与预算浪费。
 * 仅在配置了 `app.infrastructure.redis-enabled=true` 时激活。
 */
@Service
@ConditionalOnProperty(name = "app.infrastructure.redis-enabled", havingValue = "true")
public class RedisFrequencyCapService implements FrequencyCapService {

    // Spring Redis 核心操作客户端
    private final StringRedisTemplate redis;

    // 滑动窗口频控原子操作 Lua 脚本
    private final DefaultRedisScript<Long> freqScript;

    /**
     * 构造 Redis 分布式频控服务并预编译 Lua 脚本
     *
     * @param redis Redis 操作模板
     */
    public RedisFrequencyCapService(StringRedisTemplate redis) {
        this.redis = redis;

        // Lua 脚本：原子执行 1.清理过期时间戳 -> 2.统计当前有效曝光量 -> 3.未超限则插入当前曝光并刷新TTL
        String lua =
                "local key = KEYS[1] " +
                "local now = tonumber(ARGV[1]) " +
                "local cutoff = tonumber(ARGV[2]) " +
                "local limit = tonumber(ARGV[3]) " +
                "local ttl = tonumber(ARGV[4]) " +
                // 步骤1：移除窗口起始时间之前的过期历史曝光数据
                "redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff) " +
                // 步骤2：获取当前窗口内的有效曝光记录数
                "local count = redis.call('ZCARD', key) " +
                // 步骤3：判断是否已达到限制上限
                "if count >= limit then " +
                "    return 0 " + // 已达频控上限，拒绝曝光
                "end " +
                // 步骤4：未超限，记录本次曝光时间戳
                "redis.call('ZADD', key, now, now) " +
                // 步骤5：重置 Key 的过期时间，防止冷数据常驻内存
                "redis.call('EXPIRE', key, ttl) " +
                "return 1 "; // 允许曝光并计数成功

        this.freqScript = new DefaultRedisScript<>(lua, Long.class);
    }

    /**
     * 校验当前请求是否符合频控限制；若未超限则原子递增曝光计数
     *
     * @param tenantId   租户唯一标识
     * @param campaignId 广告活动唯一标识
     * @param userId     用户/设备唯一标识（如 Cookie ID、IDFA、GAID）
     * @param limit      时间窗口内允许的最大曝光次数
     * @param window     滑动时间窗口时长（例如 Duration.ofHours(1)）
     * @return true: 允许曝光（已原子记录）；false: 已达上限拦截
     */
    @Override
    public boolean checkAndIncrement(String tenantId, String campaignId, String userId, int limit, Duration window) {
        // 白名单用户或未配置限额时直接放行
        if (userId == null || userId.isBlank() || limit <= 0) {
            return true;
        }

        // 构造频控 Redis 复合键名：freq:{租户}:{活动}:{用户ID}
        String key = "freq:" + tenantId + ":" + campaignId + ":" + userId;

        // 计算当前毫秒戳与滑动窗口起始临界时间戳
        long nowMilli = Instant.now().toEpochMilli();
        long cutoffMilli = nowMilli - window.toMillis();
        long ttlSeconds = Math.max(60, window.toSeconds() * 2);

        // 执行原子 Lua 脚本
        Long result = redis.execute(
                freqScript,
                List.of(key),
                String.valueOf(nowMilli),
                String.valueOf(cutoffMilli),
                String.valueOf(limit),
                String.valueOf(ttlSeconds)
        );

        // 脚本返回 1 代表未超限且成功记录
        return result != null && result == 1L;
    }

    /**
     * 重置清除指定用户的频控记录（用于测试重置或风控解冻）
     *
     * @param tenantId   租户标识
     * @param campaignId 广告活动标识
     * @param userId     用户标识
     */
    @Override
    public void reset(String tenantId, String campaignId, String userId) {
        String key = "freq:" + tenantId + ":" + campaignId + ":" + userId;
        redis.delete(key);
    }
}
