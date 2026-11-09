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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spring Cloud Gateway 全局全链路追踪染色与边缘鉴权过滤器 (Edge Auth & Distributed Trace Global Filter)
 * <p>
 * 核心职责：
 * 1. 全链路 Trace 染色：自动提取或生成 {@code X-Trace-Id}，向下游微服务透传并回写至响应头；
 * 2. 公开数据面快速放行：对高并发公开端点（{@code /clk/**}, {@code /s2s/**}, {@code /rtb/**},
 *    {@code /api/v1/auth/**}, {@code /api/v1/affiliate/postback/**}, {@code /api/v1/skan/**},
 *    {@code /actuator/**}, {@code /fallback/**}, {@code /api/v1/gateway/**}）执行零阻塞放行；
 * 3. 控制面边缘安全拦截：当开启 {@code app.gateway.auth.enabled=true} 时，对受保护的 {@code /api/v1/**}
 *    接口强制校验 {@code Authorization: Bearer ...} 或 {@code X-API-Key} 凭证，并规范化透传 {@code X-Tenant-Id}。
 */
@Component
public class GatewayAuthAndTraceGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthAndTraceGlobalFilter.class);

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_GATEWAY_FORWARDED = "X-Gateway-Forwarded";

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/clk",
            "/s2s",
            "/rtb",
            "/affiliate/click",
            "/affiliate/postback",
            "/affiliate/skan",
            "/api/v1/auth",
            "/api/v1/affiliate/click",
            "/api/v1/affiliate/postback",
            "/api/v1/affiliate/skan",
            "/api/v1/skan",
            "/actuator",
            "/fallback",
            "/api/v1/gateway"
    );

    @Value("${app.gateway.auth.enabled:true}")
    private volatile boolean authEnabled = true;

    @Value("${app.gateway.auth.default-tenant:public}")
    private volatile String defaultTenant = "public";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @EventListener
    public void onNacosConfigRefresh(NacosConfigRefreshEvent event) {
        if (event == null || event.getUpdatedProperties() == null) {
            return;
        }
        Map<String, Object> props = event.getUpdatedProperties();
        if (props.containsKey("app.gateway.auth.enabled")) {
            this.authEnabled = Boolean.parseBoolean(String.valueOf(props.get("app.gateway.auth.enabled")));
            log.info("[GatewayAuthFilter] Hot-updated app.gateway.auth.enabled={}", this.authEnabled);
        }
        if (props.containsKey("app.gateway.auth.default-tenant")) {
            this.defaultTenant = String.valueOf(props.get("app.gateway.auth.default-tenant")).trim();
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 提取或生成全链路 X-Trace-Id
        String traceId = request.getHeaders().getFirst(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 2. 提取或补全默认租户头 X-Tenant-Id
        String tenantId = request.getHeaders().getFirst(HEADER_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = defaultTenant;
        }

        // 3. 边缘鉴权校验（仅针对非公开的控制面 /api/v1/** 路径）
        if (authEnabled && !isPublicPath(path)) {
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String apiKeyHeader = request.getHeaders().getFirst(HEADER_API_KEY);

            boolean hasBearer = authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 10;
            boolean hasApiKey = apiKeyHeader != null && !apiKeyHeader.isBlank();

            if (!hasBearer && !hasApiKey) {
                return rejectUnauthorized(exchange.getResponse(), traceId, path);
            }
        }

        // 4. 注入网关染色请求头与响应头
        final String finalTraceId = traceId;
        final String finalTenantId = tenantId;

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(HEADER_TRACE_ID, finalTraceId)
                .header(HEADER_TENANT_ID, finalTenantId)
                .header(HEADER_GATEWAY_FORWARDED, "true")
                .build();

        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, finalTraceId);
        exchange.getResponse().getHeaders().set("X-Gateway-Service", "platform-app-gateway");

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    public static boolean isPublicPath(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    private Mono<Void> rejectUnauthorized(ServerHttpResponse response, String traceId, String path) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HEADER_TRACE_ID, traceId);
        String body = String.format(
                "{\"code\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Missing Bearer token or X-API-Key at API Gateway edge\",\"path\":\"%s\",\"traceId\":\"%s\"}",
                path, traceId
        );
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
