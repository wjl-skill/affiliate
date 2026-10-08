package com.affiliate.platform.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 Redis 与 Lua 脚本的原子预算预占与资金管控服务 (Redis Atomic Budget Service)
 * <p>
 * 生产级别加固：
 * 1. 预占租约记录在 active_res ZSET 中，避免单纯依赖 TTL 自然驱逐导致未确认资金丢失；
 * 2. confirm 与 release 升级为原子 Lua 状态机流转，杜绝并发竞争或单边更新；
 * 3. 提供 sweepExpiredReservations 机制，对超时未确认的预占额度自动回补归还主预算池，并记录过期审计状态。
 * 仅在配置 `app.infrastructure.redis-enabled=true` 时激活。
 */
@Service
@ConditionalOnProperty(name = "app.infrastructure.redis-enabled", havingValue = "true")
public class RedisBudgetService implements BudgetService {

    private static final Logger log = LoggerFactory.getLogger(RedisBudgetService.class);

    // 默认预占租期：120 秒
    private static final long DEFAULT_RESERVATION_TTL_SECONDS = 120;
    // 预占详情 Redis Key 的安全兜底物理 TTL（1 小时），防止极端情况下悬挂，但实际生命周期由 active_res 租约管控
    private static final long SAFETY_STORE_TTL_SECONDS = 3600;
    // 审计状态记录保留时间：2 天
    private static final long AUDIT_RECORD_TTL_SECONDS = 172800;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> confirmScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> expireSweepScript;

    /**
     * 构造 Redis 预算服务并初始化 Lua 原子脚本
     *
     * @param redis Redis 模板
     */
    public RedisBudgetService(StringRedisTemplate redis) {
        this.redis = redis;

        // 1. 原子预占脚本
        // KEYS[1]: daily 主预算 Key
        // KEYS[2]: res:{resId} 预占详情 Key
        // KEYS[3]: active_res 租约 ZSET
        // ARGV[1]: 扣减金额 (micros)
        // ARGV[2]: resId
        // ARGV[3]: 租约过期时间戳 (epochMillis)
        // ARGV[4]: 安全兜底物理 TTL (秒)
        String reserveLua =
                "local current = redis.call('GET', KEYS[1]) " +
                "if not current then return -1 end " +
                "local remaining = tonumber(current) " +
                "local deduct = tonumber(ARGV[1]) " +
                "if remaining < deduct then return -2 end " +
                "redis.call('DECRBY', KEYS[1], deduct) " +
                "redis.call('SETEX', KEYS[2], tonumber(ARGV[4]), deduct) " +
                "redis.call('ZADD', KEYS[3], tonumber(ARGV[3]), ARGV[2]) " +
                "return remaining - deduct";
        this.reserveScript = new DefaultRedisScript<>(reserveLua, Long.class);

        // 2. 原子确认脚本
        // KEYS[1]: res:{resId}
        // KEYS[2]: active_res
        // KEYS[3]: confirmed:{resId}
        // ARGV[1]: resId
        // 返回：1=首次确认成功，2=已确认（幂等），0=不存在或已释放
        String confirmLua =
                "local exists = redis.call('EXISTS', KEYS[1]) " +
                "if exists == 1 then " +
                "  redis.call('DEL', KEYS[1]) " +
                "  redis.call('ZREM', KEYS[2], ARGV[1]) " +
                "  redis.call('SETEX', KEYS[3], " + AUDIT_RECORD_TTL_SECONDS + ", '1') " +
                "  return 1 " +
                "end " +
                "local confirmed = redis.call('EXISTS', KEYS[3]) " +
                "if confirmed == 1 then return 2 end " +
                "return 0";
        this.confirmScript = new DefaultRedisScript<>(confirmLua, Long.class);

        // 3. 原子释放脚本（主动取消/未胜出返还）
        // KEYS[1]: daily 主预算
        // KEYS[2]: res:{resId}
        // KEYS[3]: active_res
        // KEYS[4]: released:{resId}
        // ARGV[1]: resId
        // 返回：1=成功返还，2=已释放（幂等），0=预占不存在或已被转正
        String releaseLua =
                "local alreadyReleased = redis.call('EXISTS', KEYS[4]) " +
                "if alreadyReleased == 1 then return 2 end " +
                "local val = redis.call('GET', KEYS[2]) " +
                "if not val then " +
                "  redis.call('ZREM', KEYS[3], ARGV[1]) " +
                "  return 0 " +
                "end " +
                "local amount = tonumber(val) " +
                "redis.call('DEL', KEYS[2]) " +
                "redis.call('ZREM', KEYS[3], ARGV[1]) " +
                "redis.call('INCRBY', KEYS[1], amount) " +
                "redis.call('SETEX', KEYS[4], " + AUDIT_RECORD_TTL_SECONDS + ", '1') " +
                "return 1";
        this.releaseScript = new DefaultRedisScript<>(releaseLua, Long.class);

        // 4. 超时扫描回收脚本
        // KEYS[1]: daily 主预算
        // KEYS[2]: res:{resId}
        // KEYS[3]: active_res
        // KEYS[4]: expired:{resId}
        // ARGV[1]: resId
        // ARGV[2]: maxExpireEpoch (当前扫描水位毫秒)
        // 返回：1=回收成功并补回主池，0=未过期或不存在
        String expireSweepLua =
                "local score = redis.call('ZSCORE', KEYS[3], ARGV[1]) " +
                "if not score or tonumber(score) > tonumber(ARGV[2]) then return 0 end " +
                "local val = redis.call('GET', KEYS[2]) " +
                "if val then " +
                "  local amount = tonumber(val) " +
                "  redis.call('INCRBY', KEYS[1], amount) " +
                "  redis.call('DEL', KEYS[2]) " +
                "end " +
                "redis.call('ZREM', KEYS[3], ARGV[1]) " +
                "redis.call('SETEX', KEYS[4], " + AUDIT_RECORD_TTL_SECONDS + ", '1') " +
                "return 1";
        this.expireSweepScript = new DefaultRedisScript<>(expireSweepLua, Long.class);
    }

    /**
     * 设置指定广告活动的主预算（单位：元/USD）
     */
    @Override
    public void setBudget(String tenantId, String campaignId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        String key = "{budget:" + tenantId + ":" + campaignId + "}:daily";
        long micros = toMicros(amount);
        redis.opsForValue().set(key, String.valueOf(micros), Duration.ofDays(2));
    }

    /**
     * 实时竞价前原子预占预算 (Budget Reservation)
     */
    @Override
    public Reservation reserve(String tenantId, String campaignId, String userId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }

        String budgetKey = "{budget:" + tenantId + ":" + campaignId + "}:daily";
        String resId = UUID.randomUUID().toString();
        String reserveKey = "{budget:" + tenantId + ":" + campaignId + "}:res:" + resId;
        String activeSetKey = "{budget:" + tenantId + ":" + campaignId + "}:active_res";
        long micros = toMicros(amount);

        long expireEpoch = Instant.now().plusSeconds(DEFAULT_RESERVATION_TTL_SECONDS).toEpochMilli();

        Long result = redis.execute(
                reserveScript,
                List.of(budgetKey, reserveKey, activeSetKey),
                String.valueOf(micros),
                resId,
                String.valueOf(expireEpoch),
                String.valueOf(SAFETY_STORE_TTL_SECONDS)
        );

        if (result == null || result < 0) {
            throw new IllegalStateException("budget exhausted");
        }

        return new Reservation(resId, tenantId, campaignId, userId, amount);
    }

    /**
     * 竞价胜出（收到 Win Notice）后确认预占 (Confirm Reservation)
     * Lua 原子执行：销毁预占凭据、移除活跃租约集合，并记录确认审计状态。
     */
    @Override
    public void confirm(Reservation reservation) {
        String reserveKey = "{budget:" + reservation.tenantId() + ":" + reservation.campaignId() + "}:res:" + reservation.id();
        String activeSetKey = "{budget:" + reservation.tenantId() + ":" + reservation.campaignId() + "}:active_res";
        String confirmedKey = "{budget:" + reservation.tenantId() + ":" + reservation.campaignId() + "}:confirmed:" + reservation.id();

        Long code = redis.execute(
                confirmScript,
                List.of(reserveKey, activeSetKey, confirmedKey),
                reservation.id()
        );

        if (code == null || code == 0) {
            log.warn("Confirm reservation {} missed or already expired/released", reservation.id());
        }
    }

    /**
     * 竞价未中标、超时或出现异常时原子释放预占金额 (Release Reservation)
     * Lua 原子执行：原路返还主预算池并记录 released 审计状态。
     */
    @Override
    public void release(Reservation reservation) {
        String budgetKey = "{budget:" + reservation.tenantId() + ":" + reservation.campaignId() + "}:daily";
        String reserveKey = "{budget:" + reservation.tenantId() + ":" + reservation.campaignId() + "}:res:" + reservation.id();
        String activeSetKey = "{budget:" + reservation.tenantId() + ":" + reservation.campaignId() + "}:active_res";
        String releasedKey = "{budget:" + reservation.tenantId() + ":" + reservation.campaignId() + "}:released:" + reservation.id();

        redis.execute(
                releaseScript,
                List.of(budgetKey, reserveKey, activeSetKey, releasedKey),
                reservation.id()
        );
    }

    /**
     * 预算退回/追加注资
     */
    @Override
    public void creditBudget(String tenantId, String campaignId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        String budgetKey = "{budget:" + tenantId + ":" + campaignId + "}:daily";
        redis.opsForValue().increment(budgetKey, toMicros(amount));
    }

    /**
     * 扫描并回收超时的活跃预占租约，自动补回主预算池 (Sweep & Replenish Expired Reservations)
     *
     * @param tenantId   租户标识
     * @param campaignId 活动标识
     * @return 本轮成功回收并补回主池的超时预占笔数
     */
    public int sweepExpiredReservations(String tenantId, String campaignId) {
        String budgetKey = "{budget:" + tenantId + ":" + campaignId + "}:daily";
        String activeSetKey = "{budget:" + tenantId + ":" + campaignId + "}:active_res";

        long nowEpoch = Instant.now().toEpochMilli();
        Set<String> expiredResIds = redis.opsForZSet().rangeByScore(activeSetKey, 0, nowEpoch);

        if (expiredResIds == null || expiredResIds.isEmpty()) {
            return 0;
        }

        int recoveredCount = 0;
        for (String resId : expiredResIds) {
            String reserveKey = "{budget:" + tenantId + ":" + campaignId + "}:res:" + resId;
            String expiredKey = "{budget:" + tenantId + ":" + campaignId + "}:expired:" + resId;

            Long res = redis.execute(
                    expireSweepScript,
                    List.of(budgetKey, reserveKey, activeSetKey, expiredKey),
                    resId,
                    String.valueOf(nowEpoch)
            );

            if (res != null && res == 1) {
                recoveredCount++;
                log.info("Successfully recovered expired budget reservation {} for campaign {}", resId, campaignId);
            }
        }
        return recoveredCount;
    }

    private static long toMicros(BigDecimal amount) {
        return amount.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
