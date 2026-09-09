package com.affiliate.platform.budget;

import com.affiliate.platform.entity.BudgetReservationEntity;
import com.affiliate.platform.entity.CampaignBudgetEntity;
import com.affiliate.platform.mapper.BudgetReservationMapper;
import com.affiliate.platform.mapper.CampaignBudgetMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 基于 MyBatis-Plus 的数据库预算账户与原子预占实现。 */
@Service
@Primary
@ConditionalOnProperty(name = "app.infrastructure.database-enabled", havingValue = "true")
public class MybatisBudgetService implements BudgetService {
    private final CampaignBudgetMapper budgetMapper;
    private final BudgetReservationMapper reservationMapper;
    public MybatisBudgetService(CampaignBudgetMapper budgetMapper, BudgetReservationMapper reservationMapper) { this.budgetMapper=budgetMapper; this.reservationMapper=reservationMapper; }

    @Override
    public void setBudget(String tenantId, String campaignId, BigDecimal amount) {
        validateAmount(amount);
        String tenant = tenantId == null ? "public" : tenantId;
        CampaignBudgetEntity current = budgetMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CampaignBudgetEntity>().eq(CampaignBudgetEntity::getTenantId, tenant).eq(CampaignBudgetEntity::getCampaignId, campaignId));
        BigDecimal daily = current == null || current.getDailyBudget() == null ? amount : current.getDailyBudget();
        CampaignBudgetEntity next = new CampaignBudgetEntity(current == null ? "budget_" + tenant + "_" + campaignId : current.getId(), tenant, campaignId, amount, daily, amount, Instant.now());
        budgetMapper.upsert(next);
    }

    @Override
    @Transactional
    public Reservation reserve(String tenantId, String campaignId, String userId, BigDecimal amount) {
        validateAmount(amount);
        String tenant = tenantId == null ? "public" : tenantId;
        Instant now = Instant.now();
        if (budgetMapper.reserveBalance(tenant, campaignId, amount, now) == 0) throw new IllegalStateException("budget exhausted");
        UUID id = UUID.randomUUID();
        reservationMapper.insert(new BudgetReservationEntity(id, tenant, campaignId, userId, amount, "RESERVED", now, null, now.plusSeconds(120)));
        return new Reservation(id.toString(), tenant, campaignId, userId, amount);
    }

    @Override
    @Transactional
    public void confirm(Reservation reservation) {
        if (reservation == null) return;
        reservationMapper.confirm(UUID.fromString(reservation.id()), Instant.now());
    }

    @Override
    @Transactional
    public void release(Reservation reservation) {
        if (reservation == null) return;
        int changed = reservationMapper.release(UUID.fromString(reservation.id()));
        if (changed > 0) budgetMapper.releaseBalance(reservation.tenantId(), reservation.campaignId(), reservation.amount(), Instant.now());
    }

    /** 回收竞价超时且仍处于 RESERVED 的资金，保证异常链路不会永久占用预算。 */
    @Scheduled(fixedDelayString = "${app.budget.reservation-reaper-ms:30000}")
    @Transactional
    public int releaseExpiredReservations() {
        int released = 0;
        Instant now = Instant.now();
        for (BudgetReservationEntity reservation : reservationMapper.findExpired(now)) {
            if (reservationMapper.releaseExpired(reservation.getId()) > 0) {
                budgetMapper.releaseBalance(reservation.getTenantId(), reservation.getCampaignId(), reservation.getAmount(), now);
                released++;
            }
        }
        return released;
    }

    private static void validateAmount(BigDecimal amount) { if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("amount must be non-negative"); }
}
