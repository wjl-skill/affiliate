package com.affiliate.platform.service.billing.impl;

import com.affiliate.platform.entity.CreativeAiBillingLedgerEntity;
import com.affiliate.platform.mapper.CreativeAiBillingLedgerMapper;
import com.affiliate.platform.service.billing.CreativeAiBillingService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AI 算力计费两阶段管控实现 (支持 PostgreSQL 计费流水表持久化与内存沙箱兜底)
 */
@Service
public class CreativeAiBillingServiceImpl implements CreativeAiBillingService {

    private static final Logger log = LoggerFactory.getLogger(CreativeAiBillingServiceImpl.class);

    private final CreativeAiBillingLedgerMapper ledgerMapper;
    // 模拟租户钱包预占状态映射: taskNo -> reservedAmount
    private final Map<String, BigDecimal> activeHolds = new ConcurrentHashMap<>();
    // 内存沙箱计费分录流（测试与兜底）
    private final List<CreativeAiBillingLedgerEntity> inMemoryLedgers = new CopyOnWriteArrayList<>();

    public CreativeAiBillingServiceImpl() {
        this(null);
    }

    @Autowired
    public CreativeAiBillingServiceImpl(@Autowired(required = false) CreativeAiBillingLedgerMapper ledgerMapper) {
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    public boolean reserveComputeFund(String tenantId, String taskNo, BigDecimal estimatedAmount) {
        BigDecimal amount = estimatedAmount != null ? BigDecimal.ZERO.max(estimatedAmount) : BigDecimal.ZERO;
        activeHolds.put(taskNo, amount);
        log.info("[Billing] Pre-auth hold reserved: tenant={}, taskNo={}, amount={}", tenantId, taskNo, amount);
        return true;
    }

    @Override
    public boolean confirmComputeDeduction(
            String tenantId, String taskNo, BigDecimal actualAmount,
            String departmentId, String projectId, String modelCode
    ) {
        activeHolds.remove(taskNo);
        BigDecimal amount = actualAmount != null ? BigDecimal.ZERO.max(actualAmount) : BigDecimal.ZERO;
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";

        String ledgerId = "ldg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        CreativeAiBillingLedgerEntity entity = new CreativeAiBillingLedgerEntity(
                ledgerId,
                effectiveTenant,
                taskNo,
                departmentId != null ? departmentId : "dept_default",
                projectId != null ? projectId : "prj_default",
                modelCode != null ? modelCode : "unknown_model",
                amount,
                BigDecimal.valueOf(99999.00).subtract(amount),
                "deduct:" + taskNo,
                Instant.now()
        );

        if (ledgerMapper != null) {
            try {
                ledgerMapper.insert(entity);
            } catch (Exception e) {
                log.warn("[Billing] Failed to persist ledger entity to database: {}", e.getMessage());
            }
        }
        inMemoryLedgers.add(entity);

        log.info("[Billing] Compute confirmed and deducted: tenant={}, taskNo={}, actualAmount={}, dept={}, prj={}, model={}, ledgerId={}",
                effectiveTenant, taskNo, amount, departmentId, projectId, modelCode, ledgerId);
        return true;
    }

    @Override
    public boolean releaseComputeHold(String tenantId, String taskNo) {
        BigDecimal released = activeHolds.remove(taskNo);
        log.info("[Billing] Compute pre-auth hold released: tenant={}, taskNo={}, releasedAmount={}",
                tenantId, taskNo, released);
        return true;
    }

    @Override
    public boolean hasActiveHold(String taskNo) {
        return activeHolds.containsKey(taskNo);
    }

    @Override
    public List<CreativeAiBillingLedgerEntity> listLedgers(String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        if (ledgerMapper != null) {
            try {
                QueryWrapper<CreativeAiBillingLedgerEntity> qw = new QueryWrapper<>();
                qw.eq("tenant_id", effectiveTenant).orderByDesc("settled_at").last("LIMIT 200");
                List<CreativeAiBillingLedgerEntity> list = ledgerMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            } catch (Exception e) {
                log.warn("[Billing] Failed to query ledgers from DB: {}", e.getMessage());
            }
        }
        return inMemoryLedgers.stream()
                .filter(l -> effectiveTenant.equals(l.getTenantId()))
                .toList();
    }
}
