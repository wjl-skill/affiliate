package com.affiliate.platform.billing;

import com.affiliate.platform.entity.WalletAccountEntity;
import com.affiliate.platform.mapper.WalletAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 金融级虚拟钱包账户管理服务 (Wallet Service - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL {@code wallet_account} 表，
 * 管理商户资金生命周期：充值入账、竞价预占冻结、胜出扣款结算、未胜出释放及退款冲正。
 * 保障单账户高并发扣减的线程安全与原子一致性。
 */
@Service
public class WalletService {

    private final WalletAccountMapper walletAccountMapper;
    private final ConcurrentMap<String, WalletAccount> accounts = new ConcurrentHashMap<>();

    public WalletService() {
        this(null);
    }

    @Autowired
    public WalletService(@Autowired(required = false) WalletAccountMapper walletAccountMapper) {
        this.walletAccountMapper = walletAccountMapper;
    }

    /**
     * 初始化或获取钱包账户
     */
    public WalletAccount getOrCreate(String tenantId, String accountId, BigDecimal initialCreditLimit) {
        if (walletAccountMapper != null) {
            WalletAccountEntity entity = walletAccountMapper.selectById(accountId);
            if (entity != null) {
                return toDomain(entity);
            }
            WalletAccount domain = new WalletAccount(
                    accountId,
                    tenantId != null ? tenantId : "public",
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
                tenantId != null ? tenantId : "public",
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
    public synchronized WalletAccount recharge(String accountId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("recharge amount must be positive");
        }
        WalletAccount curr = findDomain(accountId);
        if (curr == null) {
            curr = new WalletAccount(accountId, "public", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", Instant.now());
        }
        WalletAccount next = new WalletAccount(
                curr.accountId(),
                curr.tenantId(),
                curr.cashBalance().add(amount),
                curr.creditLimit(),
                curr.frozenAmount(),
                curr.currency(),
                Instant.now()
        );
        saveDomain(next);
        return next;
    }

    /**
     * 竞价前原子预占冻结 (Pre-Auth Hold)
     */
    public synchronized boolean preAuthHold(String accountId, BigDecimal amount) {
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
    }

    /**
     * 竞价胜出确认真实扣减 (Capture / Settlement)
     */
    public synchronized WalletAccount capture(String accountId, BigDecimal amount) {
        WalletAccount curr = findDomain(accountId);
        if (curr == null) {
            throw new IllegalStateException("account not found: " + accountId);
        }

        BigDecimal newFrozen = curr.frozenAmount().subtract(amount);
        if (newFrozen.signum() < 0) newFrozen = BigDecimal.ZERO;

        BigDecimal newCash = curr.cashBalance().subtract(amount);

        WalletAccount next = new WalletAccount(
                curr.accountId(),
                curr.tenantId(),
                newCash,
                curr.creditLimit(),
                newFrozen,
                curr.currency(),
                Instant.now()
        );
        saveDomain(next);
        return next;
    }

    /**
     * 竞价未胜出释放预占解冻 (Release Hold)
     */
    public synchronized WalletAccount releaseHold(String accountId, BigDecimal amount) {
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
    }

    private WalletAccount findDomain(String accountId) {
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
}
