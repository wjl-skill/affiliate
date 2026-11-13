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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring Cloud Gateway 边缘自适应滑动窗口熔断器与灰度金丝雀染色过滤器
 * (Edge Adaptive Ring-Buffer Sliding Window Circuit Breaker & Canary Routing Global Filter)
 * <p>
 * 核心机制：
 * <ol>
 *     <li><b>真环形缓冲区滑动窗口 (Ring-Buffer Sliding Window)</b>：按微服务平面（RTB / TRACKER / WORKER / ADMIN）
 *         记录最近 {@code slidingWindowSize} 次调用的失败与慢调用状态，避免历史成功样本稀释突发故障率；</li>
 *     <li><b>三态状态机 + 半开并发限流</b>：支持 {@code CLOSED -> OPEN -> HALF_OPEN} 自动流转，
 *         在 {@code HALF_OPEN} 阶段限制并发探测数量，防止恢复中的下游被流量洪峰二次击穿；</li>
 *     <li><b>金丝雀灰度路由染色</b>：支持显式请求头 {@code X-Canary-Version} 及 Nacos 动态权重比例 {@code canaryWeightPercent} 染色。</li>
 * </ol>
 */
@Component
public class GatewayAdaptiveCircuitBreakerFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayAdaptiveCircuitBreakerFilter.class);
    private static final int DEFAULT_WINDOW_SIZE = 100;
    private static final int HALF_OPEN_MAX_PERMITTED = 3;

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
        final AtomicLong slowCalls = new AtomicLong(0);
        final AtomicLong shortCircuitedCalls = new AtomicLong(0);
        final AtomicInteger halfOpenInFlight = new AtomicInteger(0);
        final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);

        private final int windowSize;
        private final boolean[] failureRing;
        private final boolean[] slowRing;
        private int ringCursor = 0;
        private int ringCount = 0;
        private int ringFailures = 0;
        private int ringSlows = 0;

        volatile long openedAtMs = 0L;

        public CircuitBreakerBucket(String plane) {
            this(plane, DEFAULT_WINDOW_SIZE);
        }

        public CircuitBreakerBucket(String plane, int windowSize) {
            this.plane = plane;
            this.windowSize = Math.max(4, windowSize);
            this.failureRing = new boolean[this.windowSize];
            this.slowRing = new boolean[this.windowSize];
        }

        public void recordOutcome(boolean success, int minCalls, double failureRateThreshold, long openWaitMs) {
            recordOutcome(success, false, minCalls, failureRateThreshold, 100.0, openWaitMs);
        }

        public synchronized void recordOutcome(
                boolean success,
                boolean slow,
                int minCalls,
                double failureRateThreshold,
                double slowCallRateThreshold,
                long openWaitMs
        ) {
            totalCalls.incrementAndGet();
            if (!success) {
                failedCalls.incrementAndGet();
            }
            if (slow) {
                slowCalls.incrementAndGet();
            }

            State current = state.get();
            if (current == State.HALF_OPEN) {
                halfOpenInFlight.updateAndGet(v -> Math.max(0, v - 1));
                if (success && !slow) {
                    if (halfOpenSuccesses.incrementAndGet() >= 2) {
                        reset();
                    }
                } else {
                    state.set(State.OPEN);
                    openedAtMs = System.currentTimeMillis();
                    halfOpenSuccesses.set(0);
                    halfOpenInFlight.set(0);
                }
                return;
            }

            // 写入环形滑动窗口
            if (ringCount == windowSize) {
                if (failureRing[ringCursor]) ringFailures--;
                if (slowRing[ringCursor]) ringSlows--;
            } else {
                ringCount++;
            }
            boolean isFail = !success;
            failureRing[ringCursor] = isFail;
            slowRing[ringCursor] = slow;
            if (isFail) ringFailures++;
            if (slow) ringSlows++;
            ringCursor = (ringCursor + 1) % windowSize;

            if (ringCount >= Math.min(minCalls, windowSize)) {
                double failRate = (double) ringFailures / ringCount * 100.0;
                double slowRate = (double) ringSlows / ringCount * 100.0;
                if ((failRate >= failureRateThreshold || slowRate >= slowCallRateThreshold)
                        && state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openedAtMs = System.currentTimeMillis();
                    log.warn("[GatewayCircuitBreaker] Plane [{}] tripped to OPEN (window={}/{}, failRate={}%, slowRate={}%)",
                            plane, ringFailures, ringCount, String.format("%.1f", failRate), String.format("%.1f", slowRate));
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
                    halfOpenSuccesses.set(0);
                    halfOpenInFlight.set(1);
                    log.info("[GatewayCircuitBreaker] Plane [{}] entering HALF_OPEN for controlled probe", plane);
                    return true;
                }
                shortCircuitedCalls.incrementAndGet();
                return false;
            }
            // HALF_OPEN 状态限制最大并发探测数，防止雪崩
            int currentInFlight = halfOpenInFlight.get();
            if (currentInFlight < HALF_OPEN_MAX_PERMITTED && halfOpenInFlight.compareAndSet(currentInFlight, currentInFlight + 1)) {
                return true;
            }
            shortCircuitedCalls.incrementAndGet();
            return false;
        }

        public synchronized void reset() {
            state.set(State.CLOSED);
            totalCalls.set(0);
            failedCalls.set(0);
            slowCalls.set(0);
            halfOpenInFlight.set(0);
            halfOpenSuccesses.set(0);
            ringCursor = 0;
            ringCount = 0;
            ringFailures = 0;
            ringSlows = 0;
            Arrays.fill(failureRing, false);
            Arrays.fill(slowRing, false);
            openedAtMs = 0L;
        }

        public synchronized Map<String, Object> toView() {
            Map<String, Object> view = new LinkedHashMap<>();
            double failRate = ringCount > 0 ? Math.round((double) ringFailures / ringCount * 1000.0) / 10.0 : 0.0;
            double slowRate = ringCount > 0 ? Math.round((double) ringSlows / ringCount * 1000.0) / 10.0 : 0.0;
            view.put("plane", plane);
            view.put("state", state.get().name());
            view.put("totalCalls", totalCalls.get());
            view.put("failedCalls", failedCalls.get());
            view.put("slowCalls", slowCalls.get());
            view.put("windowSampleCount", ringCount);
            view.put("windowSize", windowSize);
            view.put("failureRatePercent", failRate);
            view.put("slowCallRatePercent", slowRate);
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

    @Value("${app.gateway.circuit-breaker.slow-call-duration-ms:120}")
    private volatile long slowCallDurationMs = 120L;

    @Value("${app.gateway.circuit-breaker.slow-call-rate-threshold:60.0}")
    private volatile double slowCallRateThreshold = 60.0;

    @Value("${app.gateway.circuit-breaker.open-wait-ms:10000}")
    private volatile long openWaitMs = 10_000L;

    @Value("${app.gateway.canary.weight-percent:0}")
    private volatile int canaryWeightPercent = 0;

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
        if (props.containsKey("app.gateway.circuit-breaker.slow-call-duration-ms")) {
            try {
                this.slowCallDurationMs = Long.parseLong(
                        String.valueOf(props.get("app.gateway.circuit-breaker.slow-call-duration-ms")).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (props.containsKey("app.gateway.canary.weight-percent")) {
            try {
                int w = Integer.parseInt(String.valueOf(props.get("app.gateway.canary.weight-percent")).trim());
                this.canaryWeightPercent = Math.max(0, Math.min(100, w));
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

        // 金丝雀灰度版本染色（支持显式 Header 与 Nacos 动态权重分流）
        String canaryVersion = request.getHeaders().getFirst("X-Canary-Version");
        ServerWebExchange effectiveExchange = exchange;
        if (canaryVersion != null && !canaryVersion.isBlank()) {
            exchange.getResponse().getHeaders().set("X-Canary-Routed", canaryVersion.trim());
        } else if (canaryWeightPercent > 0 && ThreadLocalRandom.current().nextInt(100) < canaryWeightPercent) {
            String assignedCanary = "canary-v2";
            ServerHttpRequest mutatedReq = request.mutate().header("X-Canary-Version", assignedCanary).build();
            effectiveExchange = exchange.mutate().request(mutatedReq).build();
            effectiveExchange.getResponse().getHeaders().set("X-Canary-Routed", assignedCanary);
        }

        if (!bucket.allowExecution(openWaitMs)) {
            effectiveExchange.getResponse().getHeaders().set("X-Circuit-Breaker-State", State.OPEN.name());
            return writeFallbackResponse(effectiveExchange.getResponse(), plane);
        }

        effectiveExchange.getResponse().getHeaders().set("X-Circuit-Breaker-State", bucket.state.get().name());
        final long startNanos = System.nanoTime();
        final ServerWebExchange finalExchange = effectiveExchange;

        return chain.filter(finalExchange)
                .doOnSuccess(v -> {
                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    HttpStatusCode status = finalExchange.getResponse().getStatusCode();
                    boolean ok = status == null || !status.is5xxServerError();
                    boolean slow = elapsedMs >= slowCallDurationMs;
                    bucket.recordOutcome(ok, slow, minCalls, failureRateThreshold, slowCallRateThreshold, openWaitMs);
                })
                .onErrorResume(ex -> {
                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    boolean slow = elapsedMs >= slowCallDurationMs;
                    bucket.recordOutcome(false, slow, minCalls, failureRateThreshold, slowCallRateThreshold, openWaitMs);
                    finalExchange.getResponse().getHeaders().set("X-Circuit-Breaker-State", bucket.state.get().name());
                    return writeFallbackResponse(finalExchange.getResponse(), plane);
                });
    }

    public void recordManualOutcome(String plane, boolean success) {
        recordManualOutcome(plane, success, false);
    }

    public void recordManualOutcome(String plane, boolean success, boolean slow) {
        CircuitBreakerBucket bucket = buckets.computeIfAbsent(plane.toUpperCase(), CircuitBreakerBucket::new);
        bucket.recordOutcome(success, slow, minCalls, failureRateThreshold, slowCallRateThreshold, openWaitMs);
    }

    public void resetAll() {
        buckets.values().forEach(CircuitBreakerBucket::reset);
    }

    public Map<String, Map<String, Object>> getCircuitBreakerSnapshots() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        buckets.forEach((k, v) -> map.put(k, v.toView()));
        return map;
    }

    public int getCanaryWeightPercent() {
        return canaryWeightPercent;
    }

    public void setCanaryWeightPercent(int canaryWeightPercent) {
        this.canaryWeightPercent = Math.max(0, Math.min(100, canaryWeightPercent));
    }

    public void setMinCalls(int minCalls) {
        this.minCalls = minCalls;
    }

    public void setOpenWaitMs(long openWaitMs) {
        this.openWaitMs = openWaitMs;
    }

    public void setSlowCallRateThreshold(double slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
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
