package com.affiliate.platform.billing;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.entity.CurrencyFxRateEntity;
import com.affiliate.platform.mapper.CurrencyFxRateMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨境多币种汇率与外汇折算服务 (Multi-Currency FX Service)
 * <p>
 * 商业级出海平台（Tipalti / Stripe Billing）必备组件：
 * 网盟上游广告主通常以 USD 美元预付，下游全球渠道客结算可能要求 EUR 欧元、GBP 英镑、JPY 日元或 TRC-20 USDT 等；
 * 支持动态汇率表、换汇点差加点（FX Spread，默认 1.5%）与两级缓存 + PostgreSQL 持久化。
 */
@Service
public class CurrencyFxService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyFxService.class);

    private final CurrencyFxRateMapper fxRateMapper;
    private final TwoTierCache<String, BigDecimal> fxCache;

    // 默认基准币种为 USD。汇率存储为: 1 USD = X TargetCurrency
    private final Map<String, BigDecimal> fxRates = new ConcurrentHashMap<>(Map.of(
            "USD", new BigDecimal("1.0000"),
            "EUR", new BigDecimal("0.9200"),
            "GBP", new BigDecimal("0.7850"),
            "JPY", new BigDecimal("152.5000"),
            "SGD", new BigDecimal("1.3400"),
            "USDT", new BigDecimal("1.0000") // 稳定币 1:1
    ));

    // 默认换汇手续费/点差 (1.5% = 0.015)
    private BigDecimal fxSpreadFee = new BigDecimal("0.0150");

    @Autowired
    public CurrencyFxService(@Autowired(required = false) CurrencyFxRateMapper fxRateMapper,
                             @Autowired(required = false) TwoTierCacheManager cacheManager) {
        this.fxRateMapper = fxRateMapper;
        this.fxCache = cacheManager != null ? cacheManager.getOrCreate("fx_rate", BigDecimal.class) : null;
    }

    public CurrencyFxService() {
        this(null, null);
    }

    @PostConstruct
    public void init() {
        if (fxRateMapper != null) {
            try {
                List<CurrencyFxRateEntity> list = fxRateMapper.selectList(null);
                if (list != null && !list.isEmpty()) {
                    for (CurrencyFxRateEntity entity : list) {
                        if (entity.getTargetCurrency() != null && entity.getEffectiveRate() != null) {
                            fxRates.put(entity.getTargetCurrency().toUpperCase(), entity.getEffectiveRate());
                        }
                    }
                    log.info("Initialized {} FX rates from PostgreSQL billing_currency_fx_rate", list.size());
                }
            } catch (Exception e) {
                log.warn("Failed to preload FX rates from database, using defaults: {}", e.getMessage());
            }
        }
    }

    /**
     * 将 USD 美元本金折算为目标币种金额（扣除换汇点差后）
     *
     * @param amountUsd      美元本金
     * @param targetCurrency 目标币种 (EUR / GBP / JPY / USDT)
     * @return 扣减点差后渠道客实际可得的目标币种金额
     */
    public FxConversionResult convertFromUsd(BigDecimal amountUsd, String targetCurrency) {
        if (amountUsd == null || amountUsd.signum() <= 0) {
            return new FxConversionResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, targetCurrency, BigDecimal.ONE);
        }

        String cur = targetCurrency != null ? targetCurrency.toUpperCase() : "USD";
        BigDecimal rate = getRate(cur);

        // 汇兑手续费 = amountUsd * spread
        BigDecimal spreadFeeUsd = amountUsd.multiply(fxSpreadFee).setScale(4, RoundingMode.HALF_UP);
        BigDecimal netUsd = amountUsd.subtract(spreadFeeUsd);

        // 目标币种最终金额 = netUsd * rate
        BigDecimal convertedAmount = netUsd.multiply(rate).setScale(4, RoundingMode.HALF_UP);

        return new FxConversionResult(amountUsd, spreadFeeUsd, convertedAmount, cur, rate);
    }

    public void updateRate(String currency, BigDecimal rate) {
        if (currency != null && rate != null && rate.signum() > 0) {
            String cur = currency.toUpperCase();
            fxRates.put(cur, rate);

            if (fxCache != null) {
                fxCache.put(cur, rate, Duration.ofHours(24));
            }

            if (fxRateMapper != null) {
                try {
                    CurrencyFxRateEntity entity = fxRateMapper.selectById(cur);
                    if (entity == null) {
                        entity = new CurrencyFxRateEntity(cur, "USD", cur, rate, fxSpreadFee, rate, Instant.now());
                        fxRateMapper.insert(entity);
                    } else {
                        entity.setBaseRate(rate);
                        entity.setEffectiveRate(rate);
                        entity.setUpdatedAt(Instant.now());
                        fxRateMapper.updateById(entity);
                    }
                } catch (Exception e) {
                    log.error("Failed to persist FX rate update for {}: {}", cur, e.getMessage());
                }
            }
        }
    }

    public void setFxSpreadFee(BigDecimal fee) {
        if (fee != null && fee.signum() >= 0) {
            this.fxSpreadFee = fee;
        }
    }

    public BigDecimal getRate(String currency) {
        String cur = currency != null ? currency.toUpperCase() : "USD";
        if (fxCache != null) {
            Optional<BigDecimal> cached = fxCache.get(cur);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        BigDecimal rate = fxRates.getOrDefault(cur, BigDecimal.ONE);
        if (fxCache != null) {
            fxCache.put(cur, rate, Duration.ofHours(24));
        }
        return rate;
    }

    public record FxConversionResult(
            BigDecimal grossUsd,
            BigDecimal spreadFeeUsd,
            BigDecimal netTargetAmount,
            String targetCurrency,
            BigDecimal fxRate
    ) {}
}
