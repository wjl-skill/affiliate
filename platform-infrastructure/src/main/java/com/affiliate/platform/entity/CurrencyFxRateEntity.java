package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 外汇汇率与点差持久化实体 (Currency FX Rate MyBatis-Plus Entity)
 * <p>
 * 映射数据库表 `billing_currency_fx_rate`。
 */
@TableName("billing_currency_fx_rate")
public class CurrencyFxRateEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal baseRate;
    private BigDecimal spreadRate;
    private BigDecimal effectiveRate;
    private Instant updatedAt;

    public CurrencyFxRateEntity() {}

    public CurrencyFxRateEntity(String id, String sourceCurrency, String targetCurrency,
                                BigDecimal baseRate, BigDecimal spreadRate,
                                BigDecimal effectiveRate, Instant updatedAt) {
        this.id = id;
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.baseRate = baseRate;
        this.spreadRate = spreadRate;
        this.effectiveRate = effectiveRate;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(String sourceCurrency) { this.sourceCurrency = sourceCurrency; }

    public String getTargetCurrency() { return targetCurrency; }
    public void setTargetCurrency(String targetCurrency) { this.targetCurrency = targetCurrency; }

    public BigDecimal getBaseRate() { return baseRate; }
    public void setBaseRate(BigDecimal baseRate) { this.baseRate = baseRate; }

    public BigDecimal getSpreadRate() { return spreadRate; }
    public void setSpreadRate(BigDecimal spreadRate) { this.spreadRate = spreadRate; }

    public BigDecimal getEffectiveRate() { return effectiveRate; }
    public void setEffectiveRate(BigDecimal effectiveRate) { this.effectiveRate = effectiveRate; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
