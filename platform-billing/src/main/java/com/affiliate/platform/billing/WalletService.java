package com.affiliate.platform.billing;

import com.affiliate.platform.entity.LedgerLineEntity;
import com.affiliate.platform.entity.LedgerTransactionEntity;
import com.affiliate.platform.entity.WalletAccountEntity;
import com.affiliate.platform.mapper.LedgerLineMapper;
import com.affiliate.platform.mapper.LedgerTransactionMapper;
import com.affiliate.platform.mapper.WalletAccountMapper;
import com.affiliate.platform.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.util.concurrent.Striped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

/**
 * 金融级虚拟钱包账户管理服务 (Wallet Service - Immutable Double-Entry Ledger)
 * <p>
 * 遵循商业级演进路线图规范：
 * 1. 采用复式记账不可变分录 (Ledger Transaction + Ledger Line)，每笔交易借贷严格平衡 (Debit == Credit)；
 * 2. 幂等前置强校验：基于 (tenant_id, idem_key) 唯一约束消除并发重复记账风险；
 * 3. 物化余额原子性更新与条件检查：拒绝透支；
 * 4. 内置日终自动对账 (Reconciliation) 引擎：实时核对物化余额与全部不可变会计分录的净差值；
 * 5. 全面支持多租户安全隔离与跨租户审计。
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    // 平台结算与资金出入中间过渡科目
    public static final String ACCT_EXTERNAL_CLEARING = "acct_sys_external_clearing";
    public static final String ACCT_PLATFORM_REVENUE = "acct_sys_platform_revenue";

    private final WalletAccountMapper walletAccountMapper;
    private final BillingService billingService;
    private final LedgerTransactionMapper ledgerTransactionMapper;
    private final LedgerLineMapper ledgerLineMapper;

    private final ConcurrentMap<String, WalletAccount> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LedgerTransactionEntity> inMemoryTransactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<LedgerLineEntity>> inMemoryLines = new ConcurrentHashMap<>();
    private final Striped<Lock> locks = Striped.lock(1024);

    public record ReconciliationResult(
            String accountId,
            String tenantId,
            BigDecimal cashBalance,
            BigDecimal ledgerNetBalance,
            BigDecimal discrepancy,
            boolean balanced,
            Instant checkedAt
    ) {}

    public record ReversalResult(
            String reversalTxnId,
            String originalTxnId,
            String tenantId,
            String accountId,
            BigDecimal reversedAmount,
            String status,
            Instant reversedAt
    ) {}

    public WalletService() {
        this(null, null, null, null);
    }

    public WalletService(WalletAccountMapper walletAccountMapper) {
        this(walletAccountMapper, null, null, null);
    }

    public WalletService(WalletAccountMapper walletAccountMapper, BillingService billingService) {
        this(walletAccountMapper, billingService, null, null);
    }

    @Autowired
    public WalletService(
            @Autowired(required = false) WalletAccountMapper walletAccountMapper,
            @Autowired(required = false) BillingService billingService,
            @Autowired(required = false) LedgerTransactionMapper ledgerTransactionMapper,
            @Autowired(required = false) LedgerLineMapper ledgerLineMapper
    ) {
        this.walletAccountMapper = walletAccountMapper;
        this.billingService = billingService;
        this.ledgerTransactionMapper = ledgerTransactionMapper;
        this.ledgerLineMapper = ledgerLineMapper;
    }

    /**
     * 获取或初始化钱包账户
     */
    public WalletAccount getOrCreate(String tenantId, String accountId, BigDecimal initialCreditLimit) {
        String effectiveTenant = resolveTenant(tenantId);
        if (walletAccountMapper != null) {
            WalletAccountEntity entity = walletAccountMapper.selectById(accountId);
            if (entity != null) {
                return toDomain(entity);
            }
            WalletAccount domain = new WalletAccount(
                    accountId,
                    effectiveTenant,
                    BigDecimal.ZERO,
                    initialCreditLimit == null ? BigDecimal.ZERO : initialCreditLimit,
                    BigDecimal.ZERO,
                    "USD",
                    Instant.now()
            );
            WalletAccountEntity newEntity = new WalletAccountEntity(
                    domain.accountId(),
                    domain.tenantId(),
                    domain.cashBalance(),
                    domain.creditLimit(),
                    domain.frozenAmount(),
                    domain.currency(),
                    domain.updatedAt()
            );
            walletAccountMapper.insert(newEntity);
            accounts.put(accountId, domain);
            return domain;
        }

        return accounts.computeIfAbsent(accountId, id -> new WalletAccount(
                id,
                effectiveTenant,
                BigDecimal.ZERO,
                initialCreditLimit == null ? BigDecimal.ZERO : initialCreditLimit,
                BigDecimal.ZERO,
                "USD",
                Instant.now()
        ));
    }

    /**
     * 账户资金充值 (Recharge)
     */
    @Transactional(rollbackFor = Exception.class)
    public WalletAccount recharge(String accountId, BigDecimal amount) {
        return recharge(accountId, amount, null);
    }

    /**
     * 账户资金充值（支持幂等防重键与复式双分录记账）
     */
    @Transactional(rollbackFor = Exception.class)
    public WalletAccount recharge(String accountId, BigDecimal amount, String idempotencyKey) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("recharge amount must be positive");
        }

        Lock lock = locks.get(accountId);
        lock.lock();
        try {
            WalletAccount current = findDomain(accountId);
            String tenantId = current != null ? current.tenantId() : resolveTenant(null);

            // 1. 幂等性强校验：若提供幂等键且事务已存在，立即返回当前账户，杜绝重复充值
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (ledgerTransactionMapper != null) {
                    LedgerTransactionEntity existTx = ledgerTransactionMapper.findByIdemKey(tenantId, idempotencyKey);
                    if (existTx != null) {
                        log.info("[WalletService] 幂等键 '{}' 已被处理，忽略重复充值，直接返回账户: {}", idempotencyKey, accountId);
                        return findDomain(accountId);
                    }
                } else if (billingService != null && billingService.findByIdempotencyKey(idempotencyKey).isPresent()) {
                    return findDomain(accountId);
                }
            }

            // 2. 插入不可变复式记账事务主表与借贷平衡分录
            String txnId = UUID.randomUUID().toString();
            String effectiveIdem = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? idempotencyKey
                    : "recharge:" + accountId + ":" + txnId;

            LedgerTransactionEntity txn = new LedgerTransactionEntity(
                    txnId, tenantId, effectiveIdem, "RECHARGE", null, "POSTED", Instant.now(), "system"
            );
            LedgerLineEntity debitLine = new LedgerLineEntity(
                    null, txnId, tenantId, ACCT_EXTERNAL_CLEARING, "D", amount, "USD", Instant.now()
            );
            LedgerLineEntity creditLine = new LedgerLineEntity(
                    null, txnId, tenantId, accountId, "C", amount, "USD", Instant.now()
            );

            if (ledgerTransactionMapper != null && ledgerLineMapper != null) {
                ledgerTransactionMapper.insert(txn);
                ledgerLineMapper.insert(debitLine);
                ledgerLineMapper.insert(creditLine);
            }
            inMemoryTransactions.put(txnId, txn);
            inMemoryLines.put(txnId, List.of(debitLine, creditLine));

            // 3. 更新物化余额
            WalletAccount latest;
            if (walletAccountMapper != null) {
                getOrCreate(tenantId, accountId, BigDecimal.ZERO);
                int updated = walletAccountMapper.atomicRecharge(accountId, amount);
                latest = findDomain(accountId);
                if (latest != null) {
                    accounts.put(accountId, latest);
                }
            } else {
                WalletAccount curr = (current != null) ? current : new WalletAccount(accountId, tenantId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", Instant.now());
                latest = new WalletAccount(
                        curr.accountId(),
                        curr.tenantId(),
                        curr.cashBalance().add(amount),
                        curr.creditLimit(),
                        curr.frozenAmount(),
                        curr.currency(),
                        Instant.now()
                );
                saveDomain(latest);
            }

            // 兼容原有 billing_entry
            if (billingService != null && latest != null) {
                recordLedgerEntry(
                        latest.tenantId(), accountId, null,
                        BillingService.EntryType.RECHARGE, BillingService.EntryDirection.CREDIT,
                        amount, latest.currency(), effectiveIdem, "Wallet recharge: " + amount
                );
            }

            return latest;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 竞价前原子预占冻结 (Pre-Auth Hold)
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean preAuthHold(String accountId, BigDecimal amount) {
        Lock lock = locks.get(accountId);
        lock.lock();
        try {
            if (walletAccountMapper != null) {
                int updated = walletAccountMapper.atomicPreAuthHold(accountId, amount);
                if (updated > 0) {
                    WalletAccount latest = findDomain(accountId);
                    if (latest != null) accounts.put(accountId, latest);
                    return true;
                }
                return false;
            }

            WalletAccount curr = findDomain(accountId);
            if (curr == null || !curr.canHold(amount)) {
                return false;
            }

            WalletAccount next = new WalletAccount(
                    curr.accountId(),
                    curr.tenantId(),
                    curr.cashBalance(),
                    curr.creditLimit(),
                    curr.frozenAmount().add(amount),
                    curr.currency(),
                    Instant.now()
            );
            saveDomain(next);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 竞价胜出确认真实扣减 (Capture / Settlement)
     */
    @Transactional(rollbackFor = Exception.class)
    public WalletAccount capture(String accountId, BigDecimal amount) {
        return capture(accountId, amount, null, null);
    }

    /**
     * 竞价胜出确认真实扣减（带关联拍卖标识与复式借贷分录）
     */
    @Transactional(rollbackFor = Exception.class)
    public WalletAccount capture(String accountId, BigDecimal amount, String auctionId, String idempotencyKey) {
        Lock lock = locks.get(accountId);
        lock.lock();
        try {
            WalletAccount current = findDomain(accountId);
            String tenantId = current != null ? current.tenantId() : resolveTenant(null);

            // 1. 幂等前置校验
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (ledgerTransactionMapper != null) {
                    LedgerTransactionEntity existTx = ledgerTransactionMapper.findByIdemKey(tenantId, idempotencyKey);
                    if (existTx != null) {
                        log.info("[WalletService] 幂等键 '{}' 已被处理，忽略重复扣减: {}", idempotencyKey, accountId);
                        return findDomain(accountId);
                    }
                } else if (billingService != null && billingService.findByIdempotencyKey(idempotencyKey).isPresent()) {
                    return findDomain(accountId);
                }
            }

            // 2. 插入复式分录 (借: 用户钱包减少; 贷: 平台收入增加)
            String txnId = UUID.randomUUID().toString();
            String effectiveIdem = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? idempotencyKey
                    : "capture:" + accountId + ":" + txnId;

            LedgerTransactionEntity txn = new LedgerTransactionEntity(
                    txnId, tenantId, effectiveIdem, "SPEND", null, "POSTED", Instant.now(), "system"
            );
            LedgerLineEntity debitLine = new LedgerLineEntity(
                    null, txnId, tenantId, accountId, "D", amount, "USD", Instant.now()
            );
            LedgerLineEntity creditLine = new LedgerLineEntity(
                    null, txnId, tenantId, ACCT_PLATFORM_REVENUE, "C", amount, "USD", Instant.now()
            );

            if (ledgerTransactionMapper != null && ledgerLineMapper != null) {
                ledgerTransactionMapper.insert(txn);
                ledgerLineMapper.insert(debitLine);
                ledgerLineMapper.insert(creditLine);
            }
            inMemoryTransactions.put(txnId, txn);
            inMemoryLines.put(txnId, List.of(debitLine, creditLine));

            // 3. 物化余额扣减
            WalletAccount latest;
            if (walletAccountMapper != null) {
                int updated = walletAccountMapper.atomicCapture(accountId, amount);
                latest = findDomain(accountId);
                if (latest != null) {
                    accounts.put(accountId, latest);
                }
            } else {
                if (current == null) {
                    throw new IllegalStateException("account not found: " + accountId);
                }
                BigDecimal newFrozen = current.frozenAmount().subtract(amount);
                if (newFrozen.signum() < 0) newFrozen = BigDecimal.ZERO;

                BigDecimal newCash = current.cashBalance().subtract(amount);
                latest = new WalletAccount(
                        current.accountId(),
                        current.tenantId(),
                        newCash,
                        current.creditLimit(),
                        newFrozen,
                        current.currency(),
                        Instant.now()
                );
                saveDomain(latest);
            }

            // 兼容原有 billing_entry
            if (billingService != null && latest != null) {
                recordLedgerEntry(
                        latest.tenantId(), accountId, auctionId,
                        BillingService.EntryType.ADVERTISER_CHARGE, BillingService.EntryDirection.DEBIT,
                        amount, latest.currency(), effectiveIdem, "Auction win capture: " + amount
                );
            }

            return latest;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 竞价未胜出释放预占金额 (Release Hold)
     */
    public WalletAccount releaseHold(String accountId, BigDecimal amount) {
        Lock lock = locks.get(accountId);
        lock.lock();
        try {
            if (walletAccountMapper != null) {
                int updated = walletAccountMapper.atomicRelease(accountId, amount);
                if (updated > 0) {
                    WalletAccount latest = findDomain(accountId);
                    if (latest != null) {
                        accounts.put(accountId, latest);
                        return latest;
                    }
                }
            }

            WalletAccount curr = findDomain(accountId);
            if (curr == null) return null;

            BigDecimal newFrozen = curr.frozenAmount().subtract(amount);
            if (newFrozen.signum() < 0) newFrozen = BigDecimal.ZERO;

            WalletAccount next = new WalletAccount(
                    curr.accountId(),
                    curr.tenantId(),
                    curr.cashBalance(),
                    curr.creditLimit(),
                    newFrozen,
                    curr.currency(),
                    Instant.now()
            );
            saveDomain(next);
            return next;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 账户日终对账：核对物化余额与全部不可变会计分录的净差值
     */
    public ReconciliationResult reconcile(String tenantId, String accountId) {
        WalletAccount account = findDomain(accountId);
        String effectiveTenant;
        if (tenantId != null && !tenantId.isBlank()) {
            effectiveTenant = tenantId.trim();
        } else if (account != null && account.tenantId() != null && !account.tenantId().isBlank()) {
            effectiveTenant = account.tenantId();
        } else {
            effectiveTenant = resolveTenant(null);
        }
        BigDecimal currentCash = account != null ? account.cashBalance() : BigDecimal.ZERO;

        BigDecimal netLedger = BigDecimal.ZERO;
        if (ledgerLineMapper != null) {
            netLedger = ledgerLineMapper.calculateNetBalance(effectiveTenant, accountId);
            if (netLedger == null) netLedger = BigDecimal.ZERO;
        } else {
            for (List<LedgerLineEntity> lines : inMemoryLines.values()) {
                for (LedgerLineEntity line : lines) {
                    if (effectiveTenant.equals(line.getTenantId()) && accountId.equals(line.getAccountId())) {
                        if ("C".equalsIgnoreCase(line.getDirection())) {
                            netLedger = netLedger.add(line.getAmount());
                        } else if ("D".equalsIgnoreCase(line.getDirection())) {
                            netLedger = netLedger.subtract(line.getAmount());
                        }
                    }
                }
            }
        }

        BigDecimal discrepancy = currentCash.subtract(netLedger).abs();
        boolean balanced = discrepancy.compareTo(new BigDecimal("0.000001")) <= 0;

        if (!balanced) {
            log.error("[CRITICAL AUDIT] 账户对账不平衡！账户={}, 租户={}, 物化余额={}, 账本净额={}, 差异={}",
                    accountId, effectiveTenant, currentCash, netLedger, discrepancy);
        } else {
            log.info("[Reconciliation] 账户借贷分录与物化余额 100% 平衡: accountId={}, balance={}", accountId, currentCash);
        }

        return new ReconciliationResult(
                accountId, effectiveTenant, currentCash, netLedger, discrepancy, balanced, Instant.now()
        );
    }

    /**
     * 列出指定租户（或全部）的钱包账户 ID 列表
     */
    public List<String> listAccountIds(String tenantId) {
        if (walletAccountMapper != null) {
            QueryWrapper<WalletAccountEntity> qw = new QueryWrapper<>();
            if (tenantId != null && !tenantId.isBlank()) {
                qw.eq("tenant_id", tenantId.trim());
            }
            List<WalletAccountEntity> entities = walletAccountMapper.selectList(qw);
            if (entities != null) {
                return entities.stream().map(WalletAccountEntity::getAccountId).toList();
            }
        }
        return accounts.values().stream()
                .filter(a -> tenantId == null || tenantId.isBlank() || a.tenantId().equals(tenantId.trim()))
                .map(WalletAccount::accountId)
                .toList();
    }

    /**
     * 批量自动对账：扫描指定租户（或全部账户）并核对借贷平衡
     */
    public List<ReconciliationResult> reconcileAll(String tenantId) {
        List<String> accountIds = listAccountIds(tenantId);
        List<ReconciliationResult> results = new ArrayList<>();
        for (String accId : accountIds) {
            results.add(reconcile(tenantId, accId));
        }
        return results;
    }

    /**
     * 冲正已提交的交易记录 (Double-Entry Ledger Reversal with Four-Eyes Policy)
     * <p>
     * 业务规则：
     * 1. 严格四眼复核原则：经办人 (makerId) 与复核审批人 (checkerId) 必须为不同主体；
     * 2. 原始交易必须处于 POSTED 状态且未曾被冲正过 (reverses_id 关联防重)；
     * 3. 严格生成红字/反向借贷分录并保持借贷平衡；
     * 4. 在同一事务内联动更新账户物化余额。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReversalResult reverseTransaction(
            String tenantId,
            String originalTxnId,
            String reason,
            String makerId,
            String checkerId,
            String idempotencyKey
    ) {
        if (makerId == null || makerId.isBlank() || checkerId == null || checkerId.isBlank()) {
            throw new IllegalArgumentException("四眼审批要求：经办人 (maker) 与复核人 (checker) 均不能为空");
        }
        if (makerId.trim().equalsIgnoreCase(checkerId.trim())) {
            throw new IllegalArgumentException("四眼审批违背：经办人与复核人不能为同一人 (Maker and checker cannot be identical: " + makerId + ")");
        }

        String effectiveTenant = resolveTenant(tenantId);
        String effectiveIdem = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : "reversal:" + originalTxnId + ":" + UUID.randomUUID();

        // 1. 查验原始交易
        LedgerTransactionEntity originalTxn = null;
        if (ledgerTransactionMapper != null) {
            originalTxn = ledgerTransactionMapper.selectById(originalTxnId);
        } else {
            originalTxn = inMemoryTransactions.get(originalTxnId);
        }
        if (originalTxn == null) {
            throw new IllegalArgumentException("原始会计交易不存在: " + originalTxnId);
        }
        if (!originalTxn.getTenantId().equals(effectiveTenant)) {
            throw new SecurityException("非法越权访问：原始交易不属于当前租户");
        }
        if (!"POSTED".equalsIgnoreCase(originalTxn.getStatus())) {
            throw new IllegalStateException("原始交易状态非 POSTED (" + originalTxn.getStatus() + ")，无法执行冲正");
        }
        if ("REVERSAL".equalsIgnoreCase(originalTxn.getType())) {
            throw new IllegalStateException("严禁对冲正分录本身再次执行冲正");
        }

        // 查验是否已被冲正
        if (ledgerTransactionMapper != null) {
            LedgerTransactionEntity existReversal = ledgerTransactionMapper.findByReversesId(originalTxnId);
            if (existReversal != null) {
                throw new IllegalStateException("原始交易已冲正，冲正记录 ID: " + existReversal.getId());
            }
        } else {
            boolean alreadyReversed = inMemoryTransactions.values().stream()
                    .anyMatch(t -> originalTxnId.equals(t.getReversesId()));
            if (alreadyReversed) {
                throw new IllegalStateException("原始交易已冲正，严禁重复冲正");
            }
        }

        // 2. 幂等检查
        if (ledgerTransactionMapper != null) {
            LedgerTransactionEntity existingIdem = ledgerTransactionMapper.findByIdemKey(effectiveTenant, effectiveIdem);
            if (existingIdem != null) {
                log.info("[WalletService] 冲正幂等键命中，返回已有冲正交易: {}", existingIdem.getId());
                return new ReversalResult(existingIdem.getId(), originalTxnId, effectiveTenant, "", BigDecimal.ZERO, "POSTED", existingIdem.getCreatedAt());
            }
        }

        // 3. 读取原始借贷分录明细
        List<LedgerLineEntity> originalLines;
        if (ledgerLineMapper != null) {
            originalLines = ledgerLineMapper.listByTxnId(originalTxnId);
        } else {
            originalLines = inMemoryLines.getOrDefault(originalTxnId, List.of());
        }
        if (originalLines == null || originalLines.isEmpty()) {
            throw new IllegalStateException("原始会计交易未关联任何分录明细");
        }

        // 4. 创建冲正交易头
        String reversalTxnId = UUID.randomUUID().toString();
        LedgerTransactionEntity reversalTxn = new LedgerTransactionEntity(
                reversalTxnId,
                effectiveTenant,
                effectiveIdem,
                "REVERSAL",
                originalTxnId,
                "POSTED",
                Instant.now(),
                "maker:" + makerId.trim() + ",checker:" + checkerId.trim() + ",reason:" + (reason != null ? reason : "")
        );
        if (ledgerTransactionMapper != null) {
            ledgerTransactionMapper.insert(reversalTxn);
        }
        inMemoryTransactions.put(reversalTxnId, reversalTxn);

        // 5. 生成反向借贷分录明细并插入
        List<LedgerLineEntity> reversalLines = new ArrayList<>();
        BigDecimal totalReversedAmount = BigDecimal.ZERO;
        String affectedCustomerAccount = null;

        for (LedgerLineEntity line : originalLines) {
            String invertedDirection = "D".equalsIgnoreCase(line.getDirection()) ? "C" : "D";
            LedgerLineEntity revLine = new LedgerLineEntity(
                    null,
                    reversalTxnId,
                    effectiveTenant,
                    line.getAccountId(),
                    invertedDirection,
                    line.getAmount(),
                    line.getCurrency(),
                    Instant.now()
            );
            if (ledgerLineMapper != null) {
                ledgerLineMapper.insert(revLine);
            }
            reversalLines.add(revLine);

            // 若是非系统过渡账户（即客户商户钱包）
            if (!ACCT_EXTERNAL_CLEARING.equals(line.getAccountId()) && !ACCT_PLATFORM_REVENUE.equals(line.getAccountId())) {
                affectedCustomerAccount = line.getAccountId();
                totalReversedAmount = line.getAmount();

                Lock lock = locks.get(line.getAccountId());
                lock.lock();
                try {
                    if ("D".equalsIgnoreCase(line.getDirection())) {
                        // 原先扣费 (D) -> 现返还 (C)
                        if (walletAccountMapper != null) {
                            walletAccountMapper.atomicRecharge(line.getAccountId(), line.getAmount());
                            WalletAccount updated = findDomain(line.getAccountId());
                            if (updated != null) accounts.put(line.getAccountId(), updated);
                        } else {
                            WalletAccount curr = findDomain(line.getAccountId());
                            if (curr != null) {
                                saveDomain(new WalletAccount(
                                        curr.accountId(), curr.tenantId(), curr.cashBalance().add(line.getAmount()),
                                        curr.creditLimit(), curr.frozenAmount(), curr.currency(), Instant.now()
                                ));
                            }
                        }
                    } else {
                        // 原先充值 (C) -> 现扣回 (D)
                        if (walletAccountMapper != null) {
                            walletAccountMapper.atomicCapture(line.getAccountId(), line.getAmount());
                            WalletAccount updated = findDomain(line.getAccountId());
                            if (updated != null) accounts.put(line.getAccountId(), updated);
                        } else {
                            WalletAccount curr = findDomain(line.getAccountId());
                            if (curr != null) {
                                saveDomain(new WalletAccount(
                                        curr.accountId(), curr.tenantId(), curr.cashBalance().subtract(line.getAmount()),
                                        curr.creditLimit(), curr.frozenAmount(), curr.currency(), Instant.now()
                                ));
                            }
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        inMemoryLines.put(reversalTxnId, reversalLines);

        log.info("[LedgerReversal] 成功冲正会计分录: 原始Txn={}, 冲正Txn={}, 影响账户={}, 金额={}, 经办人={}, 复核人={}, 原因={}",
                originalTxnId, reversalTxnId, affectedCustomerAccount, totalReversedAmount, makerId, checkerId, reason);

        return new ReversalResult(
                reversalTxnId,
                originalTxnId,
                effectiveTenant,
                affectedCustomerAccount != null ? affectedCustomerAccount : "",
                totalReversedAmount,
                "POSTED",
                reversalTxn.getCreatedAt()
        );
    }

    public LedgerTransactionEntity findTransaction(String txnId) {
        if (ledgerTransactionMapper != null) {
            return ledgerTransactionMapper.selectById(txnId);
        }
        return inMemoryTransactions.get(txnId);
    }

    public List<LedgerLineEntity> listLines(String txnId) {
        if (ledgerLineMapper != null) {
            return ledgerLineMapper.listByTxnId(txnId);
        }
        return inMemoryLines.getOrDefault(txnId, List.of());
    }

    public WalletAccount findDomain(String accountId) {
        if (walletAccountMapper != null) {
            WalletAccountEntity entity = walletAccountMapper.selectById(accountId);
            if (entity != null) {
                return toDomain(entity);
            }
        }
        return accounts.get(accountId);
    }

    private void saveDomain(WalletAccount domain) {
        accounts.put(domain.accountId(), domain);
        if (walletAccountMapper != null) {
            WalletAccountEntity entity = new WalletAccountEntity(
                    domain.accountId(),
                    domain.tenantId(),
                    domain.cashBalance(),
                    domain.creditLimit(),
                    domain.frozenAmount(),
                    domain.currency(),
                    domain.updatedAt()
            );
            if (walletAccountMapper.selectById(domain.accountId()) != null) {
                walletAccountMapper.updateById(entity);
            } else {
                walletAccountMapper.insert(entity);
            }
        }
    }

    private WalletAccount toDomain(WalletAccountEntity entity) {
        return new WalletAccount(
                entity.getAccountId(),
                entity.getTenantId(),
                entity.getCashBalance(),
                entity.getCreditLimit(),
                entity.getFrozenAmount(),
                entity.getCurrency(),
                entity.getUpdatedAt()
        );
    }

    private String resolveTenant(String candidate) {
        if (candidate != null && !candidate.isBlank()) return candidate.trim();
        String ctx = TenantContext.get();
        if (ctx != null && !ctx.isBlank()) return ctx.trim();
        return "public";
    }

    private BillingService.BillingEntry recordLedgerEntry(
            String tenantId,
            String accountId,
            String auctionId,
            BillingService.EntryType type,
            BillingService.EntryDirection direction,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String description
    ) {
        if (billingService == null) return null;
        BillingService.BillingEntry entry = new BillingService.BillingEntry(
                UUID.randomUUID().toString(),
                tenantId,
                accountId,
                auctionId,
                type,
                direction,
                amount,
                currency != null ? currency : "USD",
                idempotencyKey,
                description,
                Instant.now()
        );
        return billingService.record(entry);
    }
}
