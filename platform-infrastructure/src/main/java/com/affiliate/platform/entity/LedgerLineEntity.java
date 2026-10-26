package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 复式借贷分录明细实体 (Ledger Line Entity)
 * <p>
 * 映射数据库表 `ledger_line`，每一笔会计交易必须包含借贷平衡的分录明细。
 */
@TableName("ledger_line")
public class LedgerLineEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String txnId;
    private String tenantId;
    private String accountId;
    private String direction; // 'D' (借), 'C' (贷)
    private BigDecimal amount;
    private String currency;
    private Instant createdAt;

    public LedgerLineEntity() {}

    public LedgerLineEntity(Long id, String txnId, String tenantId, String accountId,
                            String direction, BigDecimal amount, String currency, Instant createdAt) {
        this.id = id;
        this.txnId = txnId;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
