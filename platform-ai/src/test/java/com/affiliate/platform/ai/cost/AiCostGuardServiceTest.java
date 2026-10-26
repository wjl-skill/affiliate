package com.affiliate.platform.ai.cost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCostGuardServiceTest {

    @Test
    @DisplayName("租户消费在预算范围内正常放行")
    void testBudgetCheckWithinLimitPasses() {
        AiCostGuardService costGuard = new AiCostGuardService();
        costGuard.setDailyBudget("tenant_normal", new BigDecimal("100.00"));

        assertThatCode(() -> costGuard.checkBudget("tenant_normal", new BigDecimal("10.00")))
                .doesNotThrowAnyException();

        costGuard.recordActualCost(
                "tenant_normal", "DASHSCOPE", "wan2.1", "video_generation",
                new BigDecimal("10.00"), 1200L, true
        );

        assertThat(costGuard.getDailySpend("tenant_normal")).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("租户预估消费超出日预算时立即阻断并抛出异常")
    void testBudgetCheckExceededThrows() {
        AiCostGuardService costGuard = new AiCostGuardService();
        costGuard.setDailyBudget("tenant_strict", new BigDecimal("30.00"));

        costGuard.recordActualCost(
                "tenant_strict", "DASHSCOPE", "wan2.1", "video_generation",
                new BigDecimal("25.00"), 1500L, true
        );

        // 已消费 25.00，上限 30.00。如果再申请 10.00 则超出预算，必须拦截
        assertThatThrownBy(() -> costGuard.checkBudget("tenant_strict", new BigDecimal("10.00")))
                .isInstanceOf(AiBudgetExceededException.class)
                .hasMessageContaining("每日算力预算超限");
    }
}
