package com.affiliate.platform.budget;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于本地 JVM 原子引用的内存预算服务 (In-Memory Atomic Budget Service)
 * <p>
 * 在未开启外部 Redis 时作为默认回退实现：
 * 基于 AtomicReference 的 CAS 乐观自旋机制实现无锁并发扣减，完全杜绝超卖。
 */
@Service
@ConditionalOnProperty(name = "app.infrastructure.redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryBudgetService implements BudgetService {

    // 内存余额表：Key 为 "tenantId:campaignId"，Value 为余额原子引用
    private final ConcurrentMap<String, AtomicReference<BigDecimal>> balances = new ConcurrentHashMap<>();

    // 活跃预占跟踪表：Key 为 reservationId，Value 为 Reservation 实体
    private final ConcurrentMap<String, Reservation> activeReservations = new ConcurrentHashMap<>();

    /**
     * 设置活动预算
     */
    @Override
    public void setBudget(String tenantId, String campaignId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        balances.computeIfAbsent(tenantId + ":" + campaignId, ignored -> new AtomicReference<>(BigDecimal.ZERO))
                .set(amount);
    }

    /**
     * 乐观自旋 CAS 预占扣减
     */
    @Override
    public Reservation reserve(String tenantId, String campaignId, String userId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        // 默认预置 1,000,000 充足预算便于单测与本地体验
        AtomicReference<BigDecimal> balance = balances.computeIfAbsent(
                tenantId + ":" + campaignId,
                ignored -> new AtomicReference<>(new BigDecimal("1000000"))
        );

        // 乐观 CAS 扣减循环
        while (true) {
            BigDecimal current = balance.get();
            if (current.compareTo(amount) < 0) {
                throw new IllegalStateException("budget exhausted");
            }
            if (balance.compareAndSet(current, current.subtract(amount))) {
                break;
            }
        }

        Reservation reservation = new Reservation(UUID.randomUUID().toString(), tenantId, campaignId, userId, amount);
        activeReservations.put(reservation.id(), reservation);
        return reservation;
    }

    /**
     * 胜出确认：从活跃预占表中清理临时记录
     */
    @Override
    public void confirm(Reservation reservation) {
        activeReservations.remove(reservation.id());
    }

    /**
     * 未胜出释放：返还金额至内存可用余额
     */
    @Override
    public void release(Reservation reservation) {
        if (activeReservations.remove(reservation.id()) != null) {
            balances.computeIfAbsent(
                    reservation.tenantId() + ":" + reservation.campaignId(),
                    ignored -> new AtomicReference<>(BigDecimal.ZERO)
            ).updateAndGet(v -> v.add(reservation.amount()));
        }
    }
}
