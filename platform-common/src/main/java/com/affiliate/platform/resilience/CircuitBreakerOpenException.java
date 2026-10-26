package com.affiliate.platform.resilience;

/**
 * 熔断器开启异常 (Circuit Breaker Open Exception)
 * 当目标下游服务处于熔断状态时立即抛出此异常，阻断流量并触发快速失败降级。
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private final String target;

    public CircuitBreakerOpenException(String target, String message) {
        super(message);
        this.target = target;
    }

    public String getTarget() {
        return target;
    }
}
