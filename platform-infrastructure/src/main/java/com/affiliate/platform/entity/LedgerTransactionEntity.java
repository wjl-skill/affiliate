package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 复式记账事务主表实体 (Ledger Transaction Entity)
 * <p>
 * 映射数据库表 `ledger_transaction`，记录不可变业务流水与全局幂等键。
 */
@TableName("ledger_transaction")
public class LedgerTransactionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String idemKey;
    private String type;
    private String reversesId;
    private String status;
    private Instant createdAt;
    private String createdBy;

    public LedgerTransactionEntity() {}

    public LedgerTransactionEntity(String id, String tenantId, String idemKey, String type,
                                   String reversesId, String status, Instant createdAt, String createdBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.idemKey = idemKey;
        this.type = type;
        this.reversesId = reversesId;
        this.status = status;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getIdemKey() { return idemKey; }
    public void setIdemKey(String idemKey) { this.idemKey = idemKey; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReversesId() { return reversesId; }
    public void setReversesId(String reversesId) { this.reversesId = reversesId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
