package com.affiliate.platform.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WalletAndRevenueShareTest {

    @Test
    void walletLifecycleRechargeHoldAndCapture() {
        WalletService service = new WalletService();

        // 创建初始账户（带 100 授信额度）
        WalletAccount acc = service.getOrCreate("t1", "acc_1", new BigDecimal("100.00"));
        assertEquals(0, new BigDecimal("100.00").compareTo(acc.availableBalance()));

        // 充值 500 元现金
        acc = service.recharge("acc_1", new BigDecimal("500.00"));
        assertEquals(0, new BigDecimal("500.00").compareTo(acc.cashBalance()));
        assertEquals(0, new BigDecimal("600.00").compareTo(acc.availableBalance())); // 500现金 + 100授信

        // 竞价预占 250 元
        assertTrue(service.preAuthHold("acc_1", new BigDecimal("250.00")));
        acc = service.getOrCreate("t1", "acc_1", null);
        assertEquals(0, new BigDecimal("350.00").compareTo(acc.availableBalance())); // 600 - 250
        assertEquals(0, new BigDecimal("250.00").compareTo(acc.frozenAmount()));

        // 胜出扣款转正 250 元
        acc = service.capture("acc_1", new BigDecimal("250.00"));
        assertEquals(0, new BigDecimal("250.00").compareTo(acc.cashBalance())); // 500 - 250
        assertEquals(0, BigDecimal.ZERO.compareTo(acc.frozenAmount()));
        assertEquals(0, new BigDecimal("350.00").compareTo(acc.availableBalance()));

        // 尝试超额预占 500 元 -> 可用仅 350 -> 失败
        assertFalse(service.preAuthHold("acc_1", new BigDecimal("500.00")));
    }

    @Test
    void revenueShareCalculation() {
        RevenueShareService shareService = new RevenueShareService();

        // 广告消耗 1000.00 美元，平台抽成 15% (0.15)
        RevenueShareService.PayoutSplit split = shareService.split(new BigDecimal("1000.00"), 0.15);

        assertEquals(0, new BigDecimal("150.0000").compareTo(split.platformFee()));
        assertEquals(0, new BigDecimal("850.0000").compareTo(split.publisherPayout()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(split.grossSpend()));
    }

    @Test
    void currencyFxConversionAndSpread() {
        CurrencyFxService fx = new CurrencyFxService();

        // 兑换 1000 美元到 EUR (汇率 0.92, 点差 1.5%)
        // 点差费 = 1000 * 0.015 = 15.00
        // 净本金 = 985.00
        // 欧元净额 = 985 * 0.92 = 906.2000
        CurrencyFxService.FxConversionResult res = fx.convertFromUsd(new BigDecimal("1000.00"), "EUR");

        assertEquals(0, new BigDecimal("15.0000").compareTo(res.spreadFeeUsd()));
        assertEquals(0, new BigDecimal("906.2000").compareTo(res.netTargetAmount()));
        assertEquals("EUR", res.targetCurrency());
    }

    @Test
    void massPayoutBatchGeneration() {
        CurrencyFxService fx = new CurrencyFxService();
        MassPayoutBatchService massPayout = new MassPayoutBatchService(fx);

        List<MassPayoutBatchService.PayoutCandidate> candidates = List.of(
                // 渠道 1: 余额 $500，无 W-8BEN (预提 10% 税 = $50, 净 $450 USD -> EUR)
                new MassPayoutBatchService.PayoutCandidate("aff-1", "Global Media Inc", "payout@global.com", "TIPALTI", new BigDecimal("500.00"), "EUR", false),
                // 渠道 2: 余额 $1200，有 W-8BEN (免税, 净 $1200 USD -> GBP)
                new MassPayoutBatchService.PayoutCandidate("aff-2", "UK Affiliates Ltd", "pay@ukaff.co.uk", "PAYONEER", new BigDecimal("1200.00"), "GBP", true),
                // 渠道 3: 余额 $50，未达起提门槛 $100 -> 应被自动排除过滤
                new MassPayoutBatchService.PayoutCandidate("aff-3", "Small Blogger", "blog@gmail.com", "PAYPAL", new BigDecimal("50.00"), "USD", false)
        );

        MassPayoutBatchService.PayoutBatchManifest batch = massPayout.createBatch(
                candidates, new BigDecimal("100.00"), new BigDecimal("0.10")
        );

        assertEquals(2, batch.totalPayees()); // 只有 2 个渠道满足出账
        assertEquals(0, new BigDecimal("1700.00").compareTo(batch.totalGrossUsd()));
        assertEquals(0, new BigDecimal("50.0000").compareTo(batch.totalTaxUsd()));
        assertEquals(0, new BigDecimal("1650.0000").compareTo(batch.totalNetUsd()));

        // 验证 CSV 导出
        String tipaltiCsv = massPayout.exportTipaltiCsv(batch);
        assertTrue(tipaltiCsv.contains("Global Media Inc"));
        assertTrue(tipaltiCsv.contains("UK Affiliates Ltd"));

        String payoneerCsv = massPayout.exportPayoneerCsv(batch);
        assertTrue(payoneerCsv.contains("pay@ukaff.co.uk"));

        // 验证批次下发操作
        boolean disbursed = massPayout.disburseBatch(batch.batchId());
        assertTrue(disbursed);
    }

    @Test
    void dynamicFxRateUpdate() {
        CurrencyFxService fx = new CurrencyFxService();
        fx.updateRate("GBP", new BigDecimal("0.8500"));
        assertEquals(0, new BigDecimal("0.8500").compareTo(fx.getRate("GBP")));

        CurrencyFxService.FxConversionResult res = fx.convertFromUsd(new BigDecimal("100.00"), "GBP");
        // 100 - 1.5% = 98.50 * 0.85 = 83.7250
        assertEquals(0, new BigDecimal("83.7250").compareTo(res.netTargetAmount()));
    }
}
