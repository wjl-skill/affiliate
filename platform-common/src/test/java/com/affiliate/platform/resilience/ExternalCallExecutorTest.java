package com.affiliate.platform.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalCallExecutorTest {

    @Test
    @DisplayName("只读或幂等接口在失败时执行指数重试并最终成功")
    void testRetryOnFailureSucceeds() {
        ExternalCallExecutor executor = new ExternalCallExecutor();
        AtomicInteger attempts = new AtomicInteger(0);

        ExternalCallExecutor.CallConfig config = new ExternalCallExecutor.CallConfig(
                Duration.ofSeconds(2), 3, true, 0.50, 10, Duration.ofSeconds(10)
        );

        String result = executor.execute("google-ads-api", config, () -> {
            int current = attempts.incrementAndGet();
            if (current < 3) {
                throw new RuntimeException("Transient connection reset");
            }
            return "SUCCESS_DATA";
        });

        assertThat(result).isEqualTo("SUCCESS_DATA");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("非幂等操作（如 AI 任务提交/打款渠道）在失败时严格禁止重试，防重复计费与重复放款")
    void testNonRetryableOperationFailsImmediately() {
        ExternalCallExecutor executor = new ExternalCallExecutor();
        AtomicInteger attempts = new AtomicInteger(0);

        ExternalCallExecutor.CallConfig config = ExternalCallExecutor.CallConfig.forAiSubmit();

        assertThatThrownBy(() -> executor.execute("dashscope-video-submit", config, () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Upstream 504 Gateway Timeout");
        }))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Upstream 504 Gateway Timeout");

        assertThat(attempts.get()).isEqualTo(1); // 严格仅调用 1 次，严禁重试
    }

    @Test
    @DisplayName("连续失败率达到阈值时触发熔断器 TRIP 为 OPEN，后续请求立即快速失败")
    void testCircuitBreakerTripsAndBlocksCalls() {
        ExternalCallExecutor executor = new ExternalCallExecutor();
        ExternalCallExecutor.CallConfig config = new ExternalCallExecutor.CallConfig(
                Duration.ofSeconds(1), 0, false, 0.50, 6, Duration.ofSeconds(60)
        );

        // 连续触发 5 次失败以填满滑动窗口并超阈值
        for (int i = 0; i < 5; i++) {
            try {
                executor.execute("flaky-api", config, () -> {
                    throw new RuntimeException("500 Server Error");
                });
            } catch (Exception ignored) {}
        }

        ExternalCallExecutor.CircuitBreaker cb = executor.getCircuitBreaker("flaky-api");
        assertThat(cb).isNotNull();
        assertThat(cb.getState()).isEqualTo(ExternalCallExecutor.State.OPEN);

        // 后续请求被熔断器快速阻断
        assertThatThrownBy(() -> executor.execute("flaky-api", config, () -> "never-called"))
                .isInstanceOf(CircuitBreakerOpenException.class)
                .hasMessageContaining("目标依赖服务处于熔断状态");
    }
}
