package com.affiliate.platform.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 统一外部依赖调用执行器与韧性护栏 (External Call Executor & Resilience Guard)
 * <p>
 * 遵循阶段 2 外部韧性架构规范：
 * 1. 统一熔断器状态机 (CLOSED -> OPEN -> HALF_OPEN)；
 * 2. 具备精确超时熔断保护与滑动窗口失败率计算；
 * 3. 严格遵循防重扣费原则：对 AI 提交与打款渠道禁止重试 (retryOnFailure=false)，对查询与只读幂等操作启用指数退避重试；
 * 4. 熔断开启期间实施微秒级快速失败，杜绝拖垮工作线程。
 */
public class ExternalCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExternalCallExecutor.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public record CallConfig(
            Duration timeout,
            int maxRetries,
            boolean retryOnFailure,
            double failureRateThreshold,
            int slidingWindowSize,
            Duration waitDurationInOpenState
    ) {
        public static CallConfig forGoogleAds() {
            return new CallConfig(Duration.ofSeconds(5), 3, true, 0.50, 20, Duration.ofSeconds(10));
        }

        public static CallConfig forAiSubmit() {
            // AI 任务提交：严格不重试，防止上游扣除多笔算力费用
            return new CallConfig(Duration.ofSeconds(10), 0, false, 0.50, 10, Duration.ofSeconds(15));
        }

        public static CallConfig forAiPoll() {
            // AI 任务轮询：允许指数重试
            return new CallConfig(Duration.ofSeconds(5), 3, true, 0.50, 10, Duration.ofSeconds(10));
        }

        public static CallConfig forS3() {
            return new CallConfig(Duration.ofSeconds(10), 3, true, 0.50, 20, Duration.ofSeconds(10));
        }

        public static CallConfig forPayout() {
            // 打款扣款渠道：严格不重试，防重复放款
            return new CallConfig(Duration.ofSeconds(15), 0, false, 0.50, 10, Duration.ofSeconds(30));
        }
    }

    public static class CircuitBreaker {
        private final String target;
        private final CallConfig config;
        private volatile State state = State.CLOSED;
        private volatile Instant stateChangedAt = Instant.now();

        private final Deque<Boolean> callResults = new ArrayDeque<>();
        private final Object lock = new Object();

        public CircuitBreaker(String target, CallConfig config) {
            this.target = target;
            this.config = config;
        }

        public State getState() {
            synchronized (lock) {
                if (state == State.OPEN) {
                    Duration elapsed = Duration.between(stateChangedAt, Instant.now());
                    if (elapsed.compareTo(config.waitDurationInOpenState()) >= 0) {
                        state = State.HALF_OPEN;
                        stateChangedAt = Instant.now();
                        log.info("[CircuitBreaker:{}] 冷却期已过，状态从 OPEN 转换为 HALF_OPEN 进行探活", target);
                    }
                }
                return state;
            }
        }

        public void checkPermission() {
            State current = getState();
            if (current == State.OPEN) {
                throw new CircuitBreakerOpenException(
                        target,
                        "目标依赖服务处于熔断状态 (Circuit Breaker OPEN): " + target + ", 快速失败拦截"
                );
            }
        }

        public void recordSuccess() {
            synchronized (lock) {
                if (state == State.HALF_OPEN) {
                    state = State.CLOSED;
                    stateChangedAt = Instant.now();
                    callResults.clear();
                    log.info("[CircuitBreaker:{}] 探活成功，熔断器恢复为 CLOSED", target);
                } else if (state == State.CLOSED) {
                    pushResult(true);
                }
            }
        }

        public void recordFailure() {
            synchronized (lock) {
                if (state == State.HALF_OPEN) {
                    state = State.OPEN;
                    stateChangedAt = Instant.now();
                    log.warn("[CircuitBreaker:{}] HALF_OPEN 探活失败，立即重新进入 OPEN 熔断", target);
                } else if (state == State.CLOSED) {
                    pushResult(false);
                    checkFailureRate();
                }
            }
        }

        private void pushResult(boolean success) {
            callResults.addLast(success);
            while (callResults.size() > config.slidingWindowSize()) {
                callResults.removeFirst();
            }
        }

        private void checkFailureRate() {
            if (callResults.size() < Math.min(5, config.slidingWindowSize())) {
                return;
            }
            long failCount = callResults.stream().filter(r -> !r).count();
            double rate = (double) failCount / callResults.size();
            if (rate >= config.failureRateThreshold()) {
                state = State.OPEN;
                stateChangedAt = Instant.now();
                log.error("[CRITICAL RESILIENCE] 目标依赖服务 {} 失败率达 {:.1f}%，触发熔断，状态变为 OPEN",
                        target, rate * 100);
            }
        }
    }

    private final ConcurrentMap<String, CircuitBreaker> registry = new ConcurrentHashMap<>();

    public <T> T execute(String target, CallConfig config, Supplier<T> supplier) {
        CircuitBreaker cb = registry.computeIfAbsent(target, k -> new CircuitBreaker(k, config));
        cb.checkPermission();

        int attempts = 0;
        int maxAttempts = config.retryOnFailure() ? (1 + config.maxRetries()) : 1;
        long backoffMs = 100;

        while (true) {
            attempts++;
            try {
                // 带超时执行
                CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier);
                T result = future.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
                cb.recordSuccess();
                return result;
            } catch (Exception ex) {
                Throwable cause = (ex instanceof ExecutionException && ex.getCause() != null)
                        ? ex.getCause()
                        : ex;

                if (attempts >= maxAttempts) {
                    cb.recordFailure();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException("External call failed for target: " + target, cause);
                }

                log.warn("[ExternalCall:{}] 调用失败 (第 {}/{} 次)，将在 {}ms 后退避重试: {}",
                        target, attempts, maxAttempts, backoffMs, cause.getMessage());

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("External call interrupted", ie);
                }
                backoffMs = Math.min(backoffMs * 2, 2000);
            }
        }
    }

    public CircuitBreaker getCircuitBreaker(String target) {
        return registry.get(target);
    }
}
