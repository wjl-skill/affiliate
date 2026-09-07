package com.affiliate.platform.budget;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地两级预算切片服务 (Two-Tier Local Budget Slicing Service)
 * <p>
 * 专为超低延迟 RTB 竞价（< 20ms）设计的核心资金管控组件：
 * 1. 实例本地持有以微美分（micros）计量的原子配额切片 (AtomicLong)；
 * 2. 竞价热路径直接通过本地无锁 CAS 扣减预占，单次耗时 < 1 微秒，消除每笔竞价调用远程 Redis 的网络 RTT；
 * 3. 本地切片不足时，批量异步/同步向主预算池（Redis/DB）预取大块切片并补充。
 */
@Service
public class LocalBudgetSliceService {

    // 全局主预算服务（Redis 或内存实现）
    private final BudgetService mainBudgetService;

    // 本地切片额度容器：Key 为 "tenantId:campaignId"，Value 为本地可用微美分余额原子计数器
    private final ConcurrentMap<String, AtomicLong> localSlices = new ConcurrentHashMap<>();

    // 本地活跃预占跟踪：Key 为 reservationId，Value 为对应的预占凭证对象
    private final ConcurrentMap<String, BudgetService.Reservation> localReservations = new ConcurrentHashMap<>();

    // 默认单次批量拉取的切片大小：10 USD = 10,000,000 micros
    private static final long DEFAULT_SLICE_MICROS = 10_000_000L;

    /**
     * 构造本地预算切片管理器
     *
     * @param mainBudgetService 主预算服务
     */
    public LocalBudgetSliceService(BudgetService mainBudgetService) {
        this.mainBudgetService = mainBudgetService;
    }

    /**
     * 极速本地预算预占（耗时 < 1 微秒）
     *
     * @param tenantId   租户标识
     * @param campaignId 活动标识
     * @param userId     用户标识
     * @param amount     预占金额（USD）
     * @return 预占凭据 Reservation
     */
    public BudgetService.Reservation reserveFast(String tenantId, String campaignId, String userId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        // 将输入金额转换为微美分整数
        long deductMicros = toMicros(amount);
        String sliceKey = tenantId + ":" + campaignId;
        AtomicLong slice = localSlices.computeIfAbsent(sliceKey, k -> new AtomicLong(0));

        // 阶段 1：乐观 CAS 循环扣减本地切片
        while (true) {
            long current = slice.get();
            if (current >= deductMicros) {
                // 本地切片充足，执行原子 CAS 扣减
                if (slice.compareAndSet(current, current - deductMicros)) {
                    String resId = UUID.randomUUID().toString();
                    BudgetService.Reservation res = new BudgetService.Reservation(resId, tenantId, campaignId, userId, amount);
                    localReservations.put(resId, res);
                    return res;
                }
            } else {
                // 阶段 2：本地切片不足，双重检查后从全局主预算批量拉取新切片
                synchronized (slice) {
                    if (slice.get() < deductMicros) {
                        try {
                            // 批量拉取 10 USD 或至少 10 倍当前请求量的大切片
                            BigDecimal sliceFetchAmount = BigDecimal.valueOf(Math.max(DEFAULT_SLICE_MICROS, deductMicros * 10) / 1_000_000.0);
                            mainBudgetService.reserve(tenantId, campaignId, "local_slice_agent", sliceFetchAmount);
                            // 将拉取到的切片填充至本地原子计数器
                            slice.addAndGet(toMicros(sliceFetchAmount));
                        } catch (Exception e) {
                            // 主预算无法提供大切片（接近预算耗尽），尝试单笔精准向主预算申请
                            return mainBudgetService.reserve(tenantId, campaignId, userId, amount);
                        }
                    }
                }
            }
        }
    }

    /**
     * 竞价胜出确认
     */
    public void confirm(BudgetService.Reservation reservation) {
        localReservations.remove(reservation.id());
        mainBudgetService.confirm(reservation);
    }

    /**
     * 竞价未中标释放预算，优先返还至本地切片以便快速复用
     */
    public void release(BudgetService.Reservation reservation) {
        if (localReservations.remove(reservation.id()) != null) {
            String sliceKey = reservation.tenantId() + ":" + reservation.campaignId();
            AtomicLong slice = localSlices.get(sliceKey);
            if (slice != null) {
                slice.addAndGet(toMicros(reservation.amount()));
                return;
            }
        }
        mainBudgetService.release(reservation);
    }

    /**
     * 预热预加载本地切片额度
     */
    public void preloadSlice(String tenantId, String campaignId, BigDecimal amount) {
        String sliceKey = tenantId + ":" + campaignId;
        localSlices.computeIfAbsent(sliceKey, k -> new AtomicLong(0))
                .addAndGet(toMicros(amount));
    }

    /**
     * 清理所有本地切片与预占记录
     */
    public void clearLocalSlices() {
        localSlices.clear();
        localReservations.clear();
    }

    /**
     * 将高精度 BigDecimal 安全四舍五入换算为以微美分为单位的长整型
     */
    private static long toMicros(BigDecimal amount) {
        return amount.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
