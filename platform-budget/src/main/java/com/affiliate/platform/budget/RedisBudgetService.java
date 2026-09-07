package com.affiliate.platform.budget;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 与 Lua 脚本的原子预算预占与资金管控服务 (Redis Atomic Budget Service)
 * <p>
 * 在广告竞价撮合前执行资金原子预占，彻底消除“先读后写”或“decrement再补偿”导致的超卖风险。
 * 仅在配置 `app.infrastructure.redis-enabled=true` 时激活。
 */
@Service
@ConditionalOnProperty(name = "app.infrastructure.redis-enabled", havingValue = "true")
public class RedisBudgetService implements BudgetService {

    // Spring Redis 客户端
    private final StringRedisTemplate redis;

    // 单次网络往返 (Single RTT) 原子预占 Lua 脚本
    private final DefaultRedisScript<Long> reserveScript;

    /**
     * 构造 Redis 预算服务并初始化 Lua 原子预占脚本
     *
     * @param redis Redis 模板
     */
    public RedisBudgetService(StringRedisTemplate redis) {
        this.redis = redis;

        // Lua 脚本保证以下步骤绝对原子执行：
        // 1. 读取当前剩余预算
        // 2. 校验余额是否充足
        // 3. 执行扣减
        // 4. 创建带 120 秒 TTL 的临时预占凭据 Key
        String lua =
                "local current = redis.call('GET', KEYS[1]) " +
                "if not current then return -1 end " + // 预算未配置或已耗尽清零
                "local remaining = tonumber(current) " +
                "local deduct = tonumber(ARGV[1]) " +
                "if remaining < deduct then return -2 end " + // 余额不足，直接拒绝
                "redis.call('DECRBY', KEYS[1], deduct) " + // 原子扣除预算
                "redis.call('SETEX', KEYS[2], tonumber(ARGV[2]), deduct) " + // 写入预占记录，设置超时时间
                "return remaining - deduct"; // 返回扣减后的可用余额

        this.reserveScript = new DefaultRedisScript<>(lua, Long.class);
    }

    /**
     * 设置指定广告活动的主预算（单位：元/USD）
     *
     * @param tenantId   租户标识
     * @param campaignId 广告活动标识
     * @param amount     预算金额（以微美分精度 micros 换算持久化至 Redis）
     */
    @Override
    public void setBudget(String tenantId, String campaignId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        String key = "budget:" + tenantId + ":" + campaignId + ":daily";
        long micros = toMicros(amount);
        // 保存 2 天，便于跨天对账与统计重置
        redis.opsForValue().set(key, String.valueOf(micros), Duration.ofDays(2));
    }

    /**
     * 实时竞价前原子预占预算 (Budget Reservation)
     *
     * @param tenantId   租户标识
     * @param campaignId 活动标识
     * @param userId     用户标识
     * @param amount     单次竞价预计扣费金额
     * @return 预占凭据 Reservation 对象，包含唯一 reservationId
     * @throws IllegalStateException 预算不足或未配置时抛出
     */
    @Override
    public Reservation reserve(String tenantId, String campaignId, String userId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }

        String budgetKey = "budget:" + tenantId + ":" + campaignId + ":daily";
        String resId = UUID.randomUUID().toString();
        String reserveKey = "reservation:" + tenantId + ":" + resId;
        long micros = toMicros(amount);

        // 调用原子 Lua 脚本，预占 TTL 设为 120 秒
        Long result = redis.execute(reserveScript, List.of(budgetKey, reserveKey), String.valueOf(micros), "120");
        if (result == null || result < 0) {
            throw new IllegalStateException("budget exhausted");
        }

        return new Reservation(resId, tenantId, campaignId, userId, amount);
    }

    /**
     * 竞价胜出（收到 Win Notice）后确认预占 (Confirm Reservation)
     * <p>
     * 销毁临时预占 Key，转换为已确认结算状态，防止被超时误回滚。
     *
     * @param reservation 预占凭证
     */
    @Override
    public void confirm(Reservation reservation) {
        String reserveKey = "reservation:" + reservation.tenantId() + ":" + reservation.id();
        redis.delete(reserveKey);
        // 记录已确认流水状态，有效期 2 天用于防重与审计
        redis.opsForValue().set("confirmed_res:" + reservation.id(), "1", Duration.ofDays(2));
    }

    /**
     * 竞价未中标、超时或出现异常时释放预占金额 (Release Reservation)
     * <p>
     * 将预占金额归还主预算池，确保资金不泄漏。
     *
     * @param reservation 待释放的预占凭证
     */
    @Override
    public void release(Reservation reservation) {
        String reserveKey = "reservation:" + reservation.tenantId() + ":" + reservation.id();
        // 仅当预占 Key 存在且成功删除时才返还金额（防重复释放）
        Boolean deleted = redis.delete(reserveKey);
        if (Boolean.TRUE.equals(deleted)) {
            String budgetKey = "budget:" + reservation.tenantId() + ":" + reservation.campaignId() + ":daily";
            redis.opsForValue().increment(budgetKey, toMicros(reservation.amount()));
        }
    }

    /**
     * 统一金额换算工具：转换为微美分整数（1 USD = 1,000,000 micros）
     *
     * @param amount 金额对象
     * @return 微美分长整型
     */
    private static long toMicros(BigDecimal amount) {
        return amount.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
