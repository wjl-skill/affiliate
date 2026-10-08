package com.affiliate.platform.budget;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地两级租约式预算切片服务 (Two-Tier Lease-Based Budget Slicing Service)
 * <p>
 * 专为超低延迟 RTB 竞价（< 20ms）设计的核心资金管控组件：
 * 1. 采用强类型 SliceKey 消除原生字符串拼接与拆分冒号碰撞漏洞 (Primitive Obsession 消除)；
 * 2. 实例本地持有以微美分（micros）计量的原子配额切片 (AtomicLong)；
 * 3. 具备 30 秒切片动态租约与 10 秒后台心跳续约机制 (Lease & Heartbeat)，停机或崩溃时未消耗额度自动回收；
 * 4. 接入 Spring 事件总线监听活动暂停广播事件，跨节点秒级感知并阻断消耗；
 * 5. 具备优雅关闭（@PreDestroy）资金自动排空归还、未确认预占时间轮自动回收防泄漏。
 */
@Service
public class LocalBudgetSliceService {

    private static final Logger log = LoggerFactory.getLogger(LocalBudgetSliceService.class);

    /**
     * 强类型本地预算切片键，消除原生字符串冒号拼接碰撞
     */
    public record SliceKey(String tenantId, String campaignId) {
        public SliceKey {
            tenantId = (tenantId != null) ? tenantId : "public";
            campaignId = (campaignId != null) ? campaignId : "";
        }

        public static SliceKey of(String tenantId, String campaignId) {
            return new SliceKey(tenantId, campaignId);
        }
    }

    /**
     * 切片租约凭证：跟踪向主池预取的切片配额生命周期与心跳
     */
    public record SliceLease(
            SliceKey key,
            BudgetService.Reservation reservation,
            long sliceAmountMicros,
            AtomicLong leaseExpiresAt
    ) {
        public boolean isExpired(long now) {
            return now >= leaseExpiresAt.get();
        }

        public void extendLease(long durationMs) {
            leaseExpiresAt.set(System.currentTimeMillis() + durationMs);
        }
    }

    /**
     * 跨节点活动状态变更广播事件
     */
    public record CampaignPauseEvent(String tenantId, String campaignId, String reason, long timestamp) {}

    // 全局主预算服务（Redis 或内存实现）
    private final BudgetService mainBudgetService;

    // 本地切片额度容器：Key 为强类型 SliceKey，Value 为本地可用微美分余额原子计数器
    private final ConcurrentMap<SliceKey, AtomicLong> localSlices = new ConcurrentHashMap<>();

    // 活跃切片租约映射：跟踪每个切片在主预算池中的租约与心跳
    private final ConcurrentMap<SliceKey, SliceLease> activeLeases = new ConcurrentHashMap<>();

    // 本地活跃预占跟踪：Key 为 reservationId，Value 为带时间戳的预占记录包装
    private final ConcurrentMap<String, TimedReservation> localReservations = new ConcurrentHashMap<>();

    // 已暂停的广告活动集合：Key 为强类型 SliceKey
    private final Set<SliceKey> pausedCampaigns = ConcurrentHashMap.newKeySet();

    // 默认单次批量拉取的切片大小：10 USD = 10,000,000 micros
    private static final long DEFAULT_SLICE_MICROS = 10_000_000L;

    // 默认切片租约有效期：30 秒
    public static final long DEFAULT_LEASE_DURATION_MS = 30_000L;

    // 后台心跳续约周期：10 秒
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000L;

    // 预占凭证默认超时回收时间：5000 毫秒
    public static final long DEFAULT_RESERVATION_TIMEOUT_MS = 5_000L;

    // 定时扫描过期预占与心跳续租的后台守护线程池
    private final ScheduledExecutorService schedulerExecutor;

    public record TimedReservation(BudgetService.Reservation reservation, long createdAtMillis) {}

    /**
     * 构造本地预算切片管理器
     *
     * @param mainBudgetService 主预算服务
     */
    public LocalBudgetSliceService(BudgetService mainBudgetService) {
        this.mainBudgetService = mainBudgetService;
        this.schedulerExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "budget-slice-coordinator");
            t.setDaemon(true);
            return t;
        });

        // 启动每秒一次的超时预占清理调度
        this.schedulerExecutor.scheduleAtFixedRate(
                () -> sweepExpiredReservations(DEFAULT_RESERVATION_TIMEOUT_MS),
                1, 1, TimeUnit.SECONDS
        );

        // 启动每 10 秒一次的切片租约心跳续约调度
        this.schedulerExecutor.scheduleAtFixedRate(
                this::renewActiveLeases,
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
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
        return reserveFast(SliceKey.of(tenantId, campaignId), userId, amount);
    }

    /**
     * 极速本地预算预占（强类型 SliceKey 重载）
     */
    public BudgetService.Reservation reserveFast(SliceKey key, String userId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }

        // 状态守卫：活动已暂停，直接拦截拒绝，杜绝已下线活动持续超支
        if (pausedCampaigns.contains(key)) {
            throw new IllegalStateException("campaign is paused: " + key);
        }

        long deductMicros = toMicros(amount);
        AtomicLong slice = localSlices.computeIfAbsent(key, k -> new AtomicLong(0));

        // 阶段 1：乐观 CAS 循环扣减本地切片
        while (true) {
            if (pausedCampaigns.contains(key)) {
                throw new IllegalStateException("campaign is paused: " + key);
            }
            long current = slice.get();
            if (current >= deductMicros) {
                // 本地切片充足，执行原子 CAS 扣减
                if (slice.compareAndSet(current, current - deductMicros)) {
                    String resId = UUID.randomUUID().toString();
                    BudgetService.Reservation res = new BudgetService.Reservation(resId, key.tenantId(), key.campaignId(), userId, amount);
                    localReservations.put(resId, new TimedReservation(res, System.currentTimeMillis()));
                    return res;
                }
            } else {
                // 阶段 2：本地切片不足，双重检查后从全局主预算批量拉取新切片（附带 30s 租约）
                synchronized (slice) {
                    if (slice.get() < deductMicros) {
                        try {
                            // 批量拉取 10 USD 或至少 10 倍当前请求量的大切片
                            BigDecimal sliceFetchAmount = BigDecimal.valueOf(Math.max(DEFAULT_SLICE_MICROS, deductMicros * 10) / 1_000_000.0);
                            BudgetService.Reservation fetched = mainBudgetService.reserve(key.tenantId(), key.campaignId(), "local_slice_agent", sliceFetchAmount);
                            
                            // 登记 30 秒切片租约
                            long fetchMicros = toMicros(sliceFetchAmount);
                            SliceLease lease = new SliceLease(
                                    key,
                                    fetched,
                                    fetchMicros,
                                    new AtomicLong(System.currentTimeMillis() + DEFAULT_LEASE_DURATION_MS)
                            );
                            activeLeases.put(key, lease);

                            // 将拉取到的切片填充至本地原子计数器
                            slice.addAndGet(fetchMicros);
                        } catch (Exception e) {
                            // 主预算无法提供大切片（接近预算耗尽），尝试单笔精准向主预算申请
                            return mainBudgetService.reserve(key.tenantId(), key.campaignId(), userId, amount);
                        }
                    }
                }
            }
        }
    }


    /**
     * 周期性租约心跳续约：为存活运行中的本地切片延长主预算租约
     */
    public void renewActiveLeases() {
        long now = System.currentTimeMillis();
        for (SliceLease lease : activeLeases.values()) {
            AtomicLong slice = localSlices.get(lease.key());
            if (slice != null && slice.get() > 0) {
                lease.extendLease(DEFAULT_LEASE_DURATION_MS);
            } else {
                activeLeases.remove(lease.key());
            }
        }
    }

    /**
     * 监听跨节点活动暂停广播事件，实时阻断并排空退还本地切片
     */
    @EventListener
    public void onCampaignStatusChanged(CampaignPauseEvent event) {
        if (event != null && event.campaignId() != null) {
            log.info("Received cross-node campaign pause event: tenant={}, campaign={}, reason={}",
                    event.tenantId(), event.campaignId(), event.reason());
            pauseCampaign(event.tenantId(), event.campaignId());
        }
    }

    /**
     * 竞价胜出确认
     */
    public void confirm(BudgetService.Reservation reservation) {
        if (reservation == null) return;
        if (localReservations.remove(reservation.id()) == null) {
            // 只有直接落到主预算服务的预占才需要向主服务确认。
            mainBudgetService.confirm(reservation);
        }
    }

    /**
     * 竞价未中标释放预算，优先返还至本地切片以便极速复用
     */
    public void release(BudgetService.Reservation reservation) {
        if (reservation == null) return;
        if (localReservations.remove(reservation.id()) != null) {
            SliceKey key = SliceKey.of(reservation.tenantId(), reservation.campaignId());
            AtomicLong slice = localSlices.get(key);
            if (slice != null) {
                slice.addAndGet(toMicros(reservation.amount()));
                return;
            }
        }
        mainBudgetService.release(reservation);
    }

    /**
     * 活动暂停处理：标记暂停并立即排空本地切片返还主池
     */
    public void pauseCampaign(String tenantId, String campaignId) {
        pauseCampaign(SliceKey.of(tenantId, campaignId));
    }

    public void pauseCampaign(SliceKey key) {
        pausedCampaigns.add(key);
        drainAndReturnSlice(key);
    }

    /**
     * 活动恢复处理：解除暂停标记
     */
    public void resumeCampaign(String tenantId, String campaignId) {
        resumeCampaign(SliceKey.of(tenantId, campaignId));
    }

    public void resumeCampaign(SliceKey key) {
        pausedCampaigns.remove(key);
    }

    public boolean isCampaignPaused(String tenantId, String campaignId) {
        return pausedCampaigns.contains(SliceKey.of(tenantId, campaignId));
    }

    public boolean isCampaignPaused(SliceKey key) {
        return pausedCampaigns.contains(key);
    }

    /**
     * 排空并返还指定活动的本地切片额度至主预算池
     */
    public BigDecimal drainAndReturnSlice(String tenantId, String campaignId) {
        return drainAndReturnSlice(SliceKey.of(tenantId, campaignId));
    }

    public BigDecimal drainAndReturnSlice(SliceKey key) {
        AtomicLong slice = localSlices.get(key);
        activeLeases.remove(key);
        if (slice == null) return BigDecimal.ZERO;

        long unspentMicros = slice.getAndSet(0);
        if (unspentMicros > 0) {
            BigDecimal unspentAmount = fromMicros(unspentMicros);
            mainBudgetService.creditBudget(key.tenantId(), key.campaignId(), unspentAmount);
            log.info("Drained and returned unspent slice: key={}, amount={}", key, unspentAmount);
            return unspentAmount;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 优雅停机排空：遍历所有活动切片，将未消耗微美分全额原子退还至主预算池（强类型 Key，无字符串拆分风险）
     */
    @PreDestroy
    public void drainAndReturnAllSlices() {
        log.info("Shutting down LocalBudgetSliceService: draining all local slices to prevent budget loss...");
        for (SliceKey key : localSlices.keySet()) {
            drainAndReturnSlice(key);
        }
        if (schedulerExecutor != null) {
            schedulerExecutor.shutdown();
        }
    }

    /**
     * 自动回收超时未确认的预占流水，杜绝内存泄漏与金额永久冻结
     */
    public int sweepExpiredReservations(long maxAgeMs) {
        long now = System.currentTimeMillis();
        int recoveredCount = 0;
        for (TimedReservation timed : localReservations.values()) {
            if (now - timed.createdAtMillis() >= maxAgeMs) {
                if (localReservations.remove(timed.reservation().id()) != null) {
                    SliceKey key = SliceKey.of(timed.reservation().tenantId(), timed.reservation().campaignId());
                    AtomicLong slice = localSlices.get(key);
                    if (slice != null) {
                        slice.addAndGet(toMicros(timed.reservation().amount()));
                        recoveredCount++;
                    } else {
                        mainBudgetService.creditBudget(
                                timed.reservation().tenantId(),
                                timed.reservation().campaignId(),
                                timed.reservation().amount()
                        );
                        recoveredCount++;
                    }
                }
            }
        }
        return recoveredCount;
    }

    /**
     * 预热预加载本地切片额度
     */
    public void preloadSlice(String tenantId, String campaignId, BigDecimal amount) {
        preloadSlice(SliceKey.of(tenantId, campaignId), amount);
    }

    public void preloadSlice(SliceKey key, BigDecimal amount) {
        localSlices.computeIfAbsent(key, k -> new AtomicLong(0))
                .addAndGet(toMicros(amount));
    }

    /**
     * 清理所有本地切片与预占记录（先排空返还主预算）
     */
    public void clearLocalSlices() {
        drainAndReturnAllSlices();
        localSlices.clear();
        activeLeases.clear();
        localReservations.clear();
        pausedCampaigns.clear();
    }

    public long getLocalSliceMicros(String tenantId, String campaignId) {
        return getLocalSliceMicros(SliceKey.of(tenantId, campaignId));
    }

    public long getLocalSliceMicros(SliceKey key) {
        AtomicLong slice = localSlices.get(key);
        return slice != null ? slice.get() : 0L;
    }

    public int getActiveReservationCount() {
        return localReservations.size();
    }

    /**
     * 将高精度 BigDecimal 安全换算为以微美分为单位的长整型
     */
    private static long toMicros(BigDecimal amount) {
        return amount.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 将微美分还原为高精度 BigDecimal
     */
    private static BigDecimal fromMicros(long micros) {
        return BigDecimal.valueOf(micros, 6);
    }
}
