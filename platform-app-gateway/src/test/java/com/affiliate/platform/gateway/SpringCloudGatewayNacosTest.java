package com.affiliate.platform.gateway;

import com.affiliate.platform.gateway.config.NacosReactiveDiscoveryClientConfiguration;
import com.affiliate.platform.gateway.filter.GatewayAdaptiveCircuitBreakerFilter;
import com.affiliate.platform.gateway.filter.GatewayAuthAndTraceGlobalFilter;
import com.affiliate.platform.gateway.filter.GatewayRateLimitGlobalFilter;
import com.affiliate.platform.gateway.route.NacosDynamicRouteRepository;
import com.affiliate.platform.gateway.web.GatewayFallbackController;
import com.affiliate.platform.nacos.NacosConfigRefreshEvent;
import com.affiliate.platform.nacos.NacosDynamicConfigManager;
import com.affiliate.platform.nacos.NacosProperties;
import com.affiliate.platform.nacos.NacosServiceRegistryManager;
import com.affiliate.platform.nacos.NacosSpringCloudDiscoveryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SpringCloudGatewayNacosTest {

    @Test
    @DisplayName("1. 验证基于 Nacos 的 Spring Cloud DiscoveryClient 与 ReactiveDiscoveryClient 实例发现")
    void testNacosSpringCloudAndReactiveDiscoveryClient() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.application.name", "platform-app-gateway")
                .withProperty("server.port", "8080");

        NacosProperties props = new NacosProperties();
        props.getDiscovery().setEnabled(false);
        NacosServiceRegistryManager registry = new NacosServiceRegistryManager(props, env);
        registry.initAndRegister();

        // 注册自定义微服务节点至 Nacos 本地拓扑
        registry.registerLocalInstance("platform-app-rtb", "10.20.1.12", 8082, "DATAPLANE", Map.of("role", "rtb-bidder"));

        NacosSpringCloudDiscoveryClient discoveryClient = new NacosSpringCloudDiscoveryClient(registry);
        ReactiveDiscoveryClient reactiveClient = new NacosReactiveDiscoveryClientConfiguration()
                .nacosReactiveDiscoveryClient(discoveryClient);

        List<ServiceInstance> rtbInstances = reactiveClient.getInstances("platform-app-rtb").collectList().block();
        assertNotNull(rtbInstances);
        assertEquals(1, rtbInstances.size());
        assertEquals("10.20.1.12", rtbInstances.get(0).getHost());
        assertEquals(8082, rtbInstances.get(0).getPort());
        assertEquals("DATAPLANE", rtbInstances.get(0).getMetadata().get("nacos.cluster"));

        // 验证未显式注册的微服务也能自动通过确定性端口映射返回 lb:// 实例
        List<ServiceInstance> trackerInstances = reactiveClient.getInstances("platform-app-tracker").collectList().block();
        assertNotNull(trackerInstances);
        assertEquals(1, trackerInstances.size());
        assertEquals(8081, trackerInstances.get(0).getPort());

        List<String> services = reactiveClient.getServices().collectList().block();
        assertNotNull(services);
        assertTrue(services.containsAll(List.of(
                "platform-app-gateway",
                "platform-app-tracker",
                "platform-app-rtb",
                "platform-app-admin",
                "platform-app-worker"
        )));
    }

    @Test
    @DisplayName("2. 验证 Nacos 动态路由配置热更新与 RefreshRoutesEvent 广播")
    void testNacosDynamicRouteHotReload() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.application.name", "platform-app-gateway");
        NacosProperties props = new NacosProperties();
        props.getConfig().setEnabled(false);

        AtomicBoolean refreshRoutesFired = new AtomicBoolean(false);
        AtomicReference<NacosDynamicRouteRepository> repoRef = new AtomicReference<>();

        NacosDynamicConfigManager configManager = new NacosDynamicConfigManager(
                props,
                env,
                event -> {
                    if (event instanceof NacosConfigRefreshEvent nacosEvent && repoRef.get() != null) {
                        repoRef.get().onNacosConfigRefresh(nacosEvent);
                    } else if (event instanceof RefreshRoutesEvent) {
                        refreshRoutesFired.set(true);
                    }
                }
        );
        configManager.initConfigCenter();

        NacosDynamicRouteRepository routeRepository = new NacosDynamicRouteRepository(
                event -> {
                    if (event instanceof RefreshRoutesEvent) {
                        refreshRoutesFired.set(true);
                    }
                },
                configManager
        );
        repoRef.set(routeRepository);

        String routesYaml = """
                gateway:
                  dynamic-routes:
                    - id: rtb-canary-route
                      uri: lb://platform-app-rtb
                      order: 5
                      predicates:
                        - Path=/rtb/openrtb/2.5/bid
                      filters:
                        - AddRequestHeader=X-Canary, v2
                    - id: tracker-fast-route
                      uri: lb://platform-app-tracker
                      order: 15
                      predicates:
                        - Path=/clk/**,/s2s/**
                """;

        List<RouteDefinition> published = routeRepository.publishDynamicRoutesYaml("AFFILIATE_GROUP", routesYaml);
        assertEquals(2, published.size());
        assertTrue(refreshRoutesFired.get(), "RefreshRoutesEvent should be published upon Nacos route update");

        List<RouteDefinition> loaded = routeRepository.getRouteDefinitions().collectList().block();
        assertNotNull(loaded);
        assertEquals(2, loaded.size());
        assertTrue(routeRepository.getDynamicRouteMap().containsKey("rtb-canary-route"));
        assertEquals("lb://platform-app-rtb", routeRepository.getDynamicRouteMap().get("rtb-canary-route").getUri().toString());
    }

    @Test
    @DisplayName("3. 验证网关边缘全链路 TraceId 注入、公开数据面直通与控制面鉴权拦截")
    void testGatewayAuthAndTraceGlobalFilter() {
        GatewayAuthAndTraceGlobalFilter filter = new GatewayAuthAndTraceGlobalFilter();
        filter.setAuthEnabled(true);

        // 场景 A: 公开数据面路径 (/rtb/openrtb/2.5/bid) 无需 Token 直接放行并自动生成 X-Trace-Id
        MockServerWebExchange publicExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/rtb/openrtb/2.5/bid").build()
        );
        AtomicReference<String> downstreamTrace = new AtomicReference<>();
        filter.filter(publicExchange, ex -> {
            downstreamTrace.set(ex.getRequest().getHeaders().getFirst(GatewayAuthAndTraceGlobalFilter.HEADER_TRACE_ID));
            return Mono.empty();
        }).block();

        assertNotNull(downstreamTrace.get());
        assertEquals(downstreamTrace.get(), publicExchange.getResponse().getHeaders().getFirst("X-Trace-Id"));

        // 场景 B: 受保护控制面路径 (/api/v1/campaigns) 未携带凭证时在网关边缘直接拦截返回 401
        MockServerWebExchange unauthorizedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/campaigns").build()
        );
        filter.filter(unauthorizedExchange, ex -> Mono.empty()).block();
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedExchange.getResponse().getStatusCode());

        // 场景 C: 受保护控制面路径携带 Bearer Token 与 X-Tenant-Id 时正常放行并透传请求头
        MockServerWebExchange authorizedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test")
                        .header("X-Tenant-Id", "tenant_vip")
                        .build()
        );
        AtomicReference<String> forwardedTenant = new AtomicReference<>();
        filter.filter(authorizedExchange, ex -> {
            forwardedTenant.set(ex.getRequest().getHeaders().getFirst("X-Tenant-Id"));
            return Mono.empty();
        }).block();
        assertEquals("tenant_vip", forwardedTenant.get());
    }

    @Test
    @DisplayName("4. 验证网关分级滑动窗口限流与 Nacos 动态 QPS 阈值热更新")
    void testGatewayRateLimitAndDynamicHotUpdate() {
        GatewayRateLimitGlobalFilter rateLimitFilter = new GatewayRateLimitGlobalFilter();
        rateLimitFilter.setControlplaneQps(2);

        for (int i = 1; i <= 2; i++) {
            MockServerWebExchange ex = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/reports")
                            .header("X-Forwarded-For", "198.51.100.77")
                            .build()
            );
            rateLimitFilter.filter(ex, e -> Mono.empty()).block();
            assertNull(ex.getResponse().getStatusCode());
        }

        // 第 3 次请求超出 2 QPS 限额，返回 429 TOO_MANY_REQUESTS
        MockServerWebExchange exceededEx = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/reports")
                        .header("X-Forwarded-For", "198.51.100.77")
                        .build()
        );
        rateLimitFilter.filter(exceededEx, e -> Mono.empty()).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exceededEx.getResponse().getStatusCode());
        assertEquals(1L, rateLimitFilter.getTotalRejected());

        // 通过 NacosConfigRefreshEvent 动态上调 controlplane-qps 至 50
        rateLimitFilter.onNacosConfigRefresh(new NacosConfigRefreshEvent(
                this,
                "platform-app-gateway-prod.yaml",
                "AFFILIATE_GROUP",
                Map.of("app.gateway.rate-limit.controlplane-qps", "50")
        ));
        assertEquals(50, rateLimitFilter.getControlplaneQps());
    }

    @Test
    @DisplayName("5. 验证 OpenRTB 2.5 合规降级端点与网关拓扑查询接口")
    void testGatewayFallbackAndTopologyController() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.application.name", "platform-app-gateway");
        NacosProperties props = new NacosProperties();
        NacosServiceRegistryManager registry = new NacosServiceRegistryManager(props, env);
        registry.initAndRegister();
        NacosDynamicConfigManager configManager = new NacosDynamicConfigManager(props, env, null);
        configManager.initConfigCenter();
        NacosDynamicRouteRepository routeRepo = new NacosDynamicRouteRepository(e -> {}, configManager);
        GatewayRateLimitGlobalFilter rateLimiter = new GatewayRateLimitGlobalFilter();
        GatewayAdaptiveCircuitBreakerFilter circuitBreaker = new GatewayAdaptiveCircuitBreakerFilter();

        GatewayFallbackController controller = new GatewayFallbackController(
                registry, configManager, routeRepo, null, rateLimiter, circuitBreaker
        );

        ResponseEntity<Map<String, Object>> rtbFallback = controller.rtbFallback().block();
        assertNotNull(rtbFallback);
        assertEquals(HttpStatus.OK, rtbFallback.getStatusCode());
        assertEquals("USD", rtbFallback.getBody().get("cur"));
        assertEquals(List.of(), rtbFallback.getBody().get("seatbid"));

        ResponseEntity<Map<String, Object>> topology = controller.getGatewayTopology().block();
        assertNotNull(topology);
        assertEquals("platform-app-gateway", topology.getBody().get("gateway"));
        assertNotNull(topology.getBody().get("circuitBreakers"));
    }

    @Test
    @DisplayName("6. 验证网关自适应滑动窗口熔断器 (CLOSED -> OPEN 降级 -> resetAll 恢复) 与金丝雀路由头透传")
    void testGatewayAdaptiveCircuitBreakerAndCanaryRouting() {
        GatewayAdaptiveCircuitBreakerFilter cbFilter = new GatewayAdaptiveCircuitBreakerFilter();
        cbFilter.setMinCalls(4);

        // 连续模拟 4 次下游 RTB 503 故障，触发熔断器进入 OPEN 状态
        for (int i = 0; i < 4; i++) {
            MockServerWebExchange failEx = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/rtb/openrtb/2.5/bid")
                            .header("X-Canary-Version", "v2-gray")
                            .build()
            );
            cbFilter.filter(failEx, ex -> {
                ex.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return Mono.empty();
            }).block();
        }

        Map<String, Map<String, Object>> snapshots = cbFilter.getCircuitBreakerSnapshots();
        assertEquals("OPEN", snapshots.get("RTB").get("state"));

        // 第 5 次请求应在网关边缘直接短路降级，返回 OpenRTB 2.5 合规空 SeatBid 且 X-Circuit-Breaker-State=OPEN
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        MockServerWebExchange shortCircuitEx = MockServerWebExchange.from(
                MockServerHttpRequest.post("/rtb/openrtb/2.5/bid").build()
        );
        cbFilter.filter(shortCircuitEx, ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(downstreamCalled.get(), "Circuit breaker in OPEN state must short-circuit downstream call");
        assertEquals(HttpStatus.OK, shortCircuitEx.getResponse().getStatusCode());
        assertEquals("OPEN", shortCircuitEx.getResponse().getHeaders().getFirst("X-Circuit-Breaker-State"));
        assertEquals(1L, snapshots.get("RTB").get("shortCircuitedCalls") instanceof Number n ? n.longValue() + 1L : 1L);

        // 手动重置熔断器后恢复 CLOSED 状态
        cbFilter.resetAll();
        assertEquals("CLOSED", cbFilter.getCircuitBreakerSnapshots().get("RTB").get("state"));
    }
}
