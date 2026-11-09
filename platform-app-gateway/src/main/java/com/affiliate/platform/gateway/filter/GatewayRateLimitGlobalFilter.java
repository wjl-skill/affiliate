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
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring Cloud Gateway 边缘自适应分级滑动窗口限流器 (Edge Adaptive Rate Limiter Global Filter)
 * <p>
 * 核心特性：
 * 1. 区分高吞吐数据面（{@code /rtb/**}, {@code /clk/**}, {@code /s2s/**}）与运营控制面（{@code /api/v1/**}）独立配额；
 * 2. 联动 Nacos 动态配置中心热更新（修改 {@code app.gateway.rate-limit.dataplane-qps} 或
 *    {@code app.gateway.rate-limit.controlplane-qps} 秒级生效）；
 * 3. 自动输出标准限流响应头（{@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining}），超限时返回 429 JSON。
 */
@Component
public class GatewayRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitGlobalFilter.class);
    private static final int MAX_TRACKED_BUCKETS = 50_000;

    @Value("${app.gateway.rate-limit.enabled:true}")
    private volatile boolean enabled = true;

    @Value("${app.gateway.rate-limit.dataplane-qps:5000}")
    private volatile int dataplaneQps = 5000;

    @Value("${app.gateway.rate-limit.controlplane-qps:200}")
    private volatile int controlplaneQps = 200;

    static final class SecondWindowCounter {
        final AtomicLong windowSecond = new AtomicLong(0);
        final AtomicInteger count = new AtomicInteger(0);

        int incrementAndGet(long currentSecond) {
            long ws = windowSecond.get();
            if (ws != currentSecond) {
                if (windowSecond.compareAndSet(ws, currentSecond)) {
                    count.set(1);
                    return 1;
                }
            }
            return count.incrementAndGet();
        }
    }

    private final ConcurrentMap<String, SecondWindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong totalRejected = new AtomicLong(0);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @EventListener
    public void onNacosConfigRefresh(NacosConfigRefreshEvent event) {
        if (event == null || event.getUpdatedProperties() == null) {
            return;
        }
        Map<String, Object> props = event.getUpdatedProperties();
        if (props.containsKey("app.gateway.rate-limit.enabled")) {
            this.enabled = Boolean.parseBoolean(String.valueOf(props.get("app.gateway.rate-limit.enabled")));
        }
        if (props.containsKey("app.gateway.rate-limit.dataplane-qps")) {
            try {
                this.dataplaneQps = Integer.parseInt(String.valueOf(props.get("app.gateway.rate-limit.dataplane-qps")).trim());
                log.info("[GatewayRateLimit] Hot-updated dataplane-qps={}", this.dataplaneQps);
            } catch (NumberFormatException ignored) {
            }
        }
        if (props.containsKey("app.gateway.rate-limit.controlplane-qps")) {
            try {
                this.controlplaneQps = Integer.parseInt(String.valueOf(props.get("app.gateway.rate-limit.controlplane-qps")).trim());
                log.info("[GatewayRateLimit] Hot-updated controlplane-qps={}", this.controlplaneQps);
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
        if (path != null && (path.startsWith("/actuator") || path.startsWith("/fallback"))) {
            return chain.filter(exchange);
        }

        boolean isDataPlane = isDataPlanePath(path);
        int limitQps = isDataPlane ? dataplaneQps : controlplaneQps;
        if (limitQps <= 0) {
            return chain.filter(exchange);
        }

        String clientKey = resolveClientKey(request, isDataPlane);
        evictIfOverCapacity();

        long currentSec = System.currentTimeMillis() / 1000L;
        SecondWindowCounter counter = counters.computeIfAbsent(clientKey, k -> new SecondWindowCounter());
        int currentCount = counter.incrementAndGet(currentSec);
        int remaining = Math.max(0, limitQps - currentCount);

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set("X-RateLimit-Limit", String.valueOf(limitQps));
        response.getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));

        if (currentCount > limitQps) {
            totalRejected.incrementAndGet();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = String.format(
                    "{\"code\":429,\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Gateway rate limit exceeded (%d QPS)\",\"plane\":\"%s\"}",
                    limitQps, isDataPlane ? "DATAPLANE" : "CONTROLPLANE"
            );
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        return chain.filter(exchange);
    }

    private static boolean isDataPlanePath(String path) {
        if (path == null) return false;
        return path.startsWith("/rtb")
                || path.startsWith("/clk")
                || path.startsWith("/s2s")
                || path.startsWith("/affiliate/click")
                || path.startsWith("/affiliate/postback")
                || path.startsWith("/affiliate/skan")
                || path.startsWith("/api/v1/affiliate/click")
                || path.startsWith("/api/v1/affiliate/postback")
                || path.startsWith("/api/v1/affiliate/skan")
                || path.startsWith("/api/v1/skan");
    }

    private static String resolveClientKey(ServerHttpRequest request, boolean isDataPlane) {
        String prefix = isDataPlane ? "dp:" : "cp:";
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int commaIdx = xff.indexOf(',');
            return prefix + (commaIdx > 0 ? xff.substring(0, commaIdx).trim() : xff.trim());
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return prefix + remote.getAddress().getHostAddress();
        }
        String tenant = request.getHeaders().getFirst("X-Tenant-Id");
        return prefix + (tenant != null && !tenant.isBlank() ? tenant : "default");
    }

    private void evictIfOverCapacity() {
        if (counters.size() < MAX_TRACKED_BUCKETS) {
            return;
        }
        Iterator<String> it = counters.keySet().iterator();
        int removed = 0;
        while (it.hasNext() && removed < 1_000) {
            it.next();
            it.remove();
            removed++;
        }
    }

    public void setDataplaneQps(int dataplaneQps) {
        this.dataplaneQps = dataplaneQps;
    }

    public void setControlplaneQps(int controlplaneQps) {
        this.controlplaneQps = controlplaneQps;
    }

    public int getDataplaneQps() {
        return dataplaneQps;
    }

    public int getControlplaneQps() {
        return controlplaneQps;
    }

    public long getTotalRejected() {
        return totalRejected.get();
    }
}
