package com.affiliate.platform.billing;

import com.affiliate.platform.entity.LedgerLineEntity;
import com.affiliate.platform.entity.LedgerTransactionEntity;
import com.affiliate.platform.mapper.LedgerLineMapper;
import com.affiliate.platform.mapper.LedgerTransactionMapper;
import com.affiliate.platform.mapper.WalletAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DoubleEntryLedgerReconciliationTest {

    private LedgerTransactionMapper txnMapper;
    private LedgerLineMapper lineMapper;
    private WalletAccountMapper walletMapper;
    private WalletService walletService;

    private final List<LedgerLineEntity> recordedLines = new ArrayList<>();
    private final List<LedgerTransactionEntity> recordedTxns = new ArrayList<>();

    @BeforeEach
    void setUp() {
        txnMapper = mock(LedgerTransactionMapper.class);
        lineMapper = mock(LedgerLineMapper.class);
        walletMapper = null; // 纯逻辑与双分录测试

        recordedLines.clear();
        recordedTxns.clear();

        doAnswer(invocation -> {
            LedgerTransactionEntity e = invocation.getArgument(0);
            recordedTxns.add(e);
            return 1;
        }).when(txnMapper).insert(any(LedgerTransactionEntity.class));

        doAnswer(invocation -> {
            LedgerLineEntity e = invocation.getArgument(0);
            recordedLines.add(e);
            return 1;
        }).when(lineMapper).insert(any(LedgerLineEntity.class));

        walletService = new WalletService(walletMapper, null, txnMapper, lineMapper);
    }

    @Test
    @DisplayName("充值必须生成借贷平衡的会计双分录 (借: 外部清算, 贷: 客户钱包)")
    void testRechargeGeneratesBalancedLedgerLines() {
        String accountId = "acct_advertiser_101";
        walletService.getOrCreate("tenant_01", accountId, BigDecimal.ZERO);

        BigDecimal rechargeAmount = new BigDecimal("500.00");
        WalletAccount account = walletService.recharge(accountId, rechargeAmount, "idem_rec_001");

        assertThat(account.cashBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(recordedTxns).hasSize(1);
        assertThat(recordedLines).hasSize(2);

        LedgerLineEntity debitLine = recordedLines.stream().filter(l -> "D".equals(l.getDirection())).findFirst().orElseThrow();
        LedgerLineEntity creditLine = recordedLines.stream().filter(l -> "C".equals(l.getDirection())).findFirst().orElseThrow();

        assertThat(debitLine.getAccountId()).isEqualTo(WalletService.ACCT_EXTERNAL_CLEARING);
        assertThat(debitLine.getAmount()).isEqualByComparingTo(rechargeAmount);

        assertThat(creditLine.getAccountId()).isEqualTo(accountId);
        assertThat(creditLine.getAmount()).isEqualByComparingTo(rechargeAmount);
    }

    @Test
    @DisplayName("消费扣款生成借贷平衡分录 (借: 客户钱包, 贷: 平台收入)")
    void testCaptureGeneratesBalancedLedgerLines() {
        String accountId = "acct_advertiser_102";
        walletService.getOrCreate("tenant_01", accountId, BigDecimal.ZERO);
        walletService.recharge(accountId, new BigDecimal("1000.00"), "idem_rec_002");

        recordedLines.clear();
        BigDecimal spendAmount = new BigDecimal("120.50");
        WalletAccount captured = walletService.capture(accountId, spendAmount, "auc_999", "idem_cap_001");

        assertThat(captured.cashBalance()).isEqualByComparingTo(new BigDecimal("879.50"));
        assertThat(recordedLines).hasSize(2);

        LedgerLineEntity debitLine = recordedLines.stream().filter(l -> "D".equals(l.getDirection())).findFirst().orElseThrow();
        LedgerLineEntity creditLine = recordedLines.stream().filter(l -> "C".equals(l.getDirection())).findFirst().orElseThrow();

        assertThat(debitLine.getAccountId()).isEqualTo(accountId);
        assertThat(debitLine.getAmount()).isEqualByComparingTo(spendAmount);

        assertThat(creditLine.getAccountId()).isEqualTo(WalletService.ACCT_PLATFORM_REVENUE);
        assertThat(creditLine.getAmount()).isEqualByComparingTo(spendAmount);
    }

    @Test
    @DisplayName("幂等防重屏障：相同 idempotencyKey 再次请求立即返回原账户，绝不重复记账")
    void testIdempotentRechargeProtection() {
        String accountId = "acct_advertiser_103";
        walletService.getOrCreate("tenant_01", accountId, BigDecimal.ZERO);

        LedgerTransactionEntity existingTx = new LedgerTransactionEntity(
                "txn_exist", "tenant_01", "idem_duplicate_test", "RECHARGE", null, "POSTED", null, "system"
        );
        when(txnMapper.findByIdemKey("tenant_01", "idem_duplicate_test")).thenReturn(existingTx);

        WalletAccount acc = walletService.recharge(accountId, new BigDecimal("800.00"), "idem_duplicate_test");

        // 验证余额未增加，且没有插入新的分录
        assertThat(acc.cashBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(recordedLines).isEmpty();
    }

    @Test
    @DisplayName("日终自动对账：物化余额与不可变分录净累计一致时判定平衡")
    void testReconciliationBalanced() {
        String accountId = "acct_advertiser_104";
        walletService.getOrCreate("tenant_01", accountId, BigDecimal.ZERO);
        walletService.recharge(accountId, new BigDecimal("1000.00"));
        walletService.capture(accountId, new BigDecimal("300.00"));

        // 物化余额为 700.00
        when(lineMapper.calculateNetBalance("tenant_01", accountId)).thenReturn(new BigDecimal("700.00"));

        WalletService.ReconciliationResult result = walletService.reconcile("tenant_01", accountId);

        assertThat(result.balanced()).isTrue();
        assertThat(result.discrepancy()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.cashBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    @DisplayName("日终自动对账：人为单边账差异时精准捕获并标记不平衡")
    void testReconciliationUnbalancedDetected() {
        String accountId = "acct_advertiser_105";
        walletService.getOrCreate("tenant_01", accountId, BigDecimal.ZERO);
        walletService.recharge(accountId, new BigDecimal("1000.00"));

        // 物化余额为 1000.00，但分录统计只有 950.00 (存在 50.00 差异)
        when(lineMapper.calculateNetBalance("tenant_01", accountId)).thenReturn(new BigDecimal("950.00"));

        WalletService.ReconciliationResult result = walletService.reconcile("tenant_01", accountId);

        assertThat(result.balanced()).isFalse();
        assertThat(result.discrepancy()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("冲正成功并遵循四眼审批原则：扣款冲正后用户物化余额准确返还，且分录反向平衡")
    void testReversalWithFourEyesPolicySuccess() {
        String accountId = "acct_advertiser_106";
        walletService.getOrCreate("tenant_01", accountId, BigDecimal.ZERO);
        walletService.recharge(accountId, new BigDecimal("1000.00"));

        // 1. 产生一笔扣款
        WalletAccount captured = walletService.capture(accountId, new BigDecimal("250.00"), "auc_reversal", "idem_spend_106");
        assertThat(captured.cashBalance()).isEqualByComparingTo(new BigDecimal("750.00"));

        LedgerTransactionEntity spendTx = recordedTxns.stream()
                .filter(t -> "SPEND".equals(t.getType()))
                .findFirst().orElseThrow();
        String spendTxId = spendTx.getId();

        // 模拟 mock 行为
        when(txnMapper.selectById(spendTxId)).thenReturn(spendTx);
        when(lineMapper.listByTxnId(spendTxId)).thenReturn(List.of(
                new LedgerLineEntity(1L, spendTxId, "tenant_01", accountId, "D", new BigDecimal("250.00"), "USD", null),
                new LedgerLineEntity(2L, spendTxId, "tenant_01", WalletService.ACCT_PLATFORM_REVENUE, "C", new BigDecimal("250.00"), "USD", null)
        ));

        // 2. 经办人与复核人双人审批通过
        WalletService.ReversalResult revResult = walletService.reverseTransaction(
                "tenant_01", spendTxId, "Customer billing dispute resolved",
                "maker_operator_01", "checker_manager_02", "idem_rev_001"
        );

        assertThat(revResult.status()).isEqualTo("POSTED");
        assertThat(revResult.reversedAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(revResult.originalTxnId()).isEqualTo(spendTxId);

        // 3. 验证物化余额已全额冲正返还 (750 + 250 = 1000)
        WalletAccount restored = walletService.findDomain(accountId);
        assertThat(restored.cashBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        // 4. 验证冲正交易与反向分录
        LedgerTransactionEntity revTxn = recordedTxns.stream()
                .filter(t -> "REVERSAL".equals(t.getType()))
                .findFirst().orElseThrow();
        assertThat(revTxn.getReversesId()).isEqualTo(spendTxId);
        assertThat(revTxn.getCreatedBy()).contains("maker:maker_operator_01").contains("checker:checker_manager_02");
    }

    @Test
    @DisplayName("四眼审批违背：经办人与复核审批人相同时严禁冲正并立即抛出异常")
    void testReversalRejectsSameMakerAndChecker() {
        String spendTxId = "txn_fake_spend";
        LedgerTransactionEntity spendTx = new LedgerTransactionEntity(
                spendTxId, "tenant_01", "idem_fake", "SPEND", null, "POSTED", null, "system"
        );
        when(txnMapper.selectById(spendTxId)).thenReturn(spendTx);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> walletService.reverseTransaction(
                "tenant_01", spendTxId, "Self-approval attempt",
                "same_user", "same_user", "idem_same_user"
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("四眼审批违背：经办人与复核人不能为同一人");
    }

    @Test
    @DisplayName("冲正防重保护：已冲正交易再次冲正时必须阻断")
    void testReversalRejectsAlreadyReversedTransaction() {
        String spendTxId = "txn_already_reversed";
        LedgerTransactionEntity spendTx = new LedgerTransactionEntity(
                spendTxId, "tenant_01", "idem_fake", "SPEND", null, "POSTED", null, "system"
        );
        when(txnMapper.selectById(spendTxId)).thenReturn(spendTx);
        when(txnMapper.findByReversesId(spendTxId)).thenReturn(new LedgerTransactionEntity(
                "rev_already_exists", "tenant_01", "idem_r", "REVERSAL", spendTxId, "POSTED", null, "admin"
        ));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> walletService.reverseTransaction(
                "tenant_01", spendTxId, "Repeated reversal attempt",
                "user_maker", "user_checker", "idem_repeat"
        ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("原始交易已冲正");
    }
}
