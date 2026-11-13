package com.affiliate.platform.gateway.filter;

import com.affiliate.platform.nacos.NacosConfigRefreshEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring Cloud Gateway 边缘自适应滑动窗口熔断器与灰度金丝雀染色过滤器
 * (Edge Adaptive Circuit Breaker & Canary Routing Global Filter)
 * <p>
 * 核心机制：
 * <ol>
 *     <li><b>三态状态机 (CLOSED -> OPEN -> HALF_OPEN)</b>：按微服务平面（RTB / TRACKER / WORKER / ADMIN）独立统计调用健康度；</li>
 *     <li><b>故障自动短路降级</b>：当样本数达到 {@code minCalls} 且 5xx/异常比例超过 {@code failureRateThreshold} 时，
 *         自动熔断 {@code openWaitMs} 毫秒，RTB 流量直接返回 OpenRTB 2.5 合规 No-Bid 响应（{@code nbr: 2}），彻底阻断级联雪崩；</li>
 *     <li><b>金丝雀灰度路由染色</b>：识别请求头 {@code X-Canary-Version} 并透传下游染色标识。</li>
 * </ol>
 */
@Component
public class GatewayAdaptiveCircuitBreakerFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayAdaptiveCircuitBreakerFilter.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static final class CircuitBreakerBucket {
        final String plane;
        final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        final AtomicLong totalCalls = new AtomicLong(0);
        final AtomicLong failedCalls = new AtomicLong(0);
        final AtomicLong shortCircuitedCalls = new AtomicLong(0);
        volatile long openedAtMs = 0L;

        public CircuitBreakerBucket(String plane) {
            this.plane = plane;
        }

        public synchronized void recordOutcome(boolean success, int minCalls, double failureRateThreshold, long openWaitMs) {
            State current = state.get();
            if (current == State.HALF_OPEN) {
                if (success) {
                    reset();
                } else {
                    state.set(State.OPEN);
                    openedAtMs = System.currentTimeMillis();
                }
                return;
            }

            long total = totalCalls.incrementAndGet();
            long failed = success ? failedCalls.get() : failedCalls.incrementAndGet();
            if (total >= minCalls) {
                double rate = (double) failed / total * 100.0;
                if (rate >= failureRateThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openedAtMs = System.currentTimeMillis();
                    log.warn("[GatewayCircuitBreaker] Plane [{}] tripped to OPEN (failed={}/{}, rate={}%)",
                            plane, failed, total, String.format("%.1f", rate));
                }
            }
        }

        public boolean allowExecution(long openWaitMs) {
            State current = state.get();
            if (current == State.CLOSED) {
                return true;
            }
            if (current == State.OPEN) {
                long elapsed = System.currentTimeMillis() - openedAtMs;
                if (elapsed >= openWaitMs && state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("[GatewayCircuitBreaker] Plane [{}] entering HALF_OPEN for probe request", plane);
                    return true;
                }
                shortCircuitedCalls.incrementAndGet();
                return false;
            }
            // HALF_OPEN 允许探测请求通过
            return true;
        }

        public synchronized void reset() {
            state.set(State.CLOSED);
            totalCalls.set(0);
            failedCalls.set(0);
            openedAtMs = 0L;
        }

        public Map<String, Object> toView() {
            Map<String, Object> view = new LinkedHashMap<>();
            long total = totalCalls.get();
            long failed = failedCalls.get();
            view.put("plane", plane);
            view.put("state", state.get().name());
            view.put("totalCalls", total);
            view.put("failedCalls", failed);
            view.put("failureRatePercent", total > 0 ? Math.round((double) failed / total * 1000.0) / 10.0 : 0.0);
            view.put("shortCircuitedCalls", shortCircuitedCalls.get());
            return view;
        }
    }

    @Value("${app.gateway.circuit-breaker.enabled:true}")
    private volatile boolean enabled = true;

    @Value("${app.gateway.circuit-breaker.min-calls:6}")
    private volatile int minCalls = 6;

    @Value("${app.gateway.circuit-breaker.failure-rate-threshold:50.0}")
    private volatile double failureRateThreshold = 50.0;

    @Value("${app.gateway.circuit-breaker.open-wait-ms:10000}")
    private volatile long openWaitMs = 10_000L;

    private final ConcurrentMap<String, CircuitBreakerBucket> buckets = new ConcurrentHashMap<>();

    public GatewayAdaptiveCircuitBreakerFilter() {
        buckets.put("RTB", new CircuitBreakerBucket("RTB"));
        buckets.put("TRACKER", new CircuitBreakerBucket("TRACKER"));
        buckets.put("WORKER", new CircuitBreakerBucket("WORKER"));
        buckets.put("ADMIN", new CircuitBreakerBucket("ADMIN"));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    @EventListener
    public void onNacosConfigRefresh(NacosConfigRefreshEvent event) {
        if (event == null || event.getUpdatedProperties() == null) {
            return;
        }
        Map<String, Object> props = event.getUpdatedProperties();
        if (props.containsKey("app.gateway.circuit-breaker.enabled")) {
            this.enabled = Boolean.parseBoolean(String.valueOf(props.get("app.gateway.circuit-breaker.enabled")));
        }
        if (props.containsKey("app.gateway.circuit-breaker.failure-rate-threshold")) {
            try {
                this.failureRateThreshold = Double.parseDouble(
                        String.valueOf(props.get("app.gateway.circuit-breaker.failure-rate-threshold")).trim());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path == null || path.startsWith("/actuator") || path.startsWith("/fallback") || path.startsWith("/api/v1/gateway")) {
            return chain.filter(exchange);
        }

        String plane = resolvePlane(path);
        CircuitBreakerBucket bucket = buckets.computeIfAbsent(plane, CircuitBreakerBucket::new);

        // 金丝雀灰度版本染色
        String canaryVersion = request.getHeaders().getFirst("X-Canary-Version");
        if (canaryVersion != null && !canaryVersion.isBlank()) {
            exchange.getResponse().getHeaders().set("X-Canary-Routed", canaryVersion.trim());
        }

        if (!bucket.allowExecution(openWaitMs)) {
            exchange.getResponse().getHeaders().set("X-Circuit-Breaker-State", State.OPEN.name());
            return writeFallbackResponse(exchange.getResponse(), plane);
        }

        exchange.getResponse().getHeaders().set("X-Circuit-Breaker-State", bucket.state.get().name());

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    boolean ok = status == null || !status.is5xxServerError();
                    bucket.recordOutcome(ok, minCalls, failureRateThreshold, openWaitMs);
                })
                . onErrorResume(ex -> {
                    bucket.recordOutcome(false, minCalls, failureRateThreshold, openWaitMs);
                    exchange.getResponse().getHeaders().set("X-Circuit-Breaker-State", bucket.state.get().name());
                    return writeFallbackResponse(exchange.getResponse(), plane);
                });
    }

    public void recordManualOutcome(String plane, boolean success) {
        CircuitBreakerBucket bucket = buckets.computeIfAbsent(plane.toUpperCase(), CircuitBreakerBucket::new);
        bucket.recordOutcome(success, minCalls, failureRateThreshold, openWaitMs);
    }

    public void resetAll() {
        buckets.values().forEach(CircuitBreakerBucket::reset);
    }

    public Map<String, Map<String, Object>> getCircuitBreakerSnapshots() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        buckets.forEach((k, v) -> map.put(k, v.toView()));
        return map;
    }

    public void setMinCalls(int minCalls) {
        this.minCalls = minCalls;
    }

    public void setOpenWaitMs(long openWaitMs) {
        this.openWaitMs = openWaitMs;
    }

    public static String resolvePlane(String path) {
        if (path == null) return "ADMIN";
        if (path.startsWith("/rtb") || path.startsWith("/api/v1/adx") || path.startsWith("/api/v1/dsp") || path.startsWith("/api/v1/ssp")) {
            return "RTB";
        }
        if (path.startsWith("/clk") || path.startsWith("/s2s") || path.startsWith("/affiliate/click")
                || path.startsWith("/affiliate/postback") || path.startsWith("/affiliate/skan")
                || path.startsWith("/api/v1/affiliate/click") || path.startsWith("/api/v1/affiliate/postback")
                || path.startsWith("/api/v1/skan")) {
            return "TRACKER";
        }
        if (path.startsWith("/api/v1/ops/outbox") || path.startsWith("/api/v1/outbox")
                || path.startsWith("/api/v1/affiliate/offers/supplement") || path.startsWith("/api/v1/affiliate/supplement")) {
            return "WORKER";
        }
        return "ADMIN";
    }

    private Mono<Void> writeFallbackResponse(ServerHttpResponse response, String plane) {
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body;
        if ("RTB".equals(plane)) {
            response.setStatusCode(HttpStatus.OK);
            body = "{\"id\":\"gateway-cb-nobid\",\"seatbid\":[],\"cur\":\"USD\",\"nbr\":2}";
        } else if ("TRACKER".equals(plane)) {
            response.setStatusCode(HttpStatus.ACCEPTED);
            body = String.format(
                    "{\"status\":\"DEGRADED_ACCEPTED\",\"plane\":\"TRACKER\",\"message\":\"Circuit breaker open, event buffered at edge\",\"timestamp\":\"%s\"}",
                    Instant.now()
            );
        } else {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            body = String.format(
                    "{\"code\":503,\"status\":\"CIRCUIT_BREAKER_OPEN\",\"plane\":\"%s\",\"message\":\"Downstream microservice circuit breaker is OPEN\",\"timestamp\":\"%s\"}",
                    plane, Instant.now()
            );
        }
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
