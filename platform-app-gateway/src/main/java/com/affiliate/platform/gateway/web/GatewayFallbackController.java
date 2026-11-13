package com.affiliate.platform.gateway.web;

import com.affiliate.platform.gateway.filter.GatewayAdaptiveCircuitBreakerFilter;
import com.affiliate.platform.gateway.filter.GatewayRateLimitGlobalFilter;
import com.affiliate.platform.gateway.route.NacosDynamicRouteRepository;
import com.affiliate.platform.nacos.NacosDynamicConfigManager;
import com.affiliate.platform.nacos.NacosServiceRegistryManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Cloud Gateway 熔断降级端点与 Nacos 网关路由治理控制器
 * <p>
 * 提供：
 * 1. {@code /fallback/rtb}: OpenRTB 2.5 标准无出价降级响应（保障下游 RTB 实例重启或网络抖动时不向 SSP 抛 5xx 错误）；
 * 2. {@code /fallback/tracker}, {@code /fallback/admin}, {@code /fallback/worker}: 分平面优雅降级响应；
 * 3. {@code /api/v1/gateway/topology}: 查询网关当前 Nacos 服务发现拓扑、生效路由表与限流统计；
 * 4. {@code /api/v1/gateway/routes/publish}: 在线向 Nacos 发布并热更新网关动态路由规则。
 */
@RestController
public class GatewayFallbackController {

    private final NacosServiceRegistryManager registryManager;
    private final NacosDynamicConfigManager configManager;
    private final NacosDynamicRouteRepository dynamicRouteRepository;
    private final RouteDefinitionLocator routeDefinitionLocator;
    private final GatewayRateLimitGlobalFilter rateLimitFilter;
    private final GatewayAdaptiveCircuitBreakerFilter circuitBreakerFilter;

    public GatewayFallbackController(
            NacosServiceRegistryManager registryManager,
            NacosDynamicConfigManager configManager,
            NacosDynamicRouteRepository dynamicRouteRepository,
            RouteDefinitionLocator routeDefinitionLocator,
            GatewayRateLimitGlobalFilter rateLimitFilter
    ) {
        this(registryManager, configManager, dynamicRouteRepository, routeDefinitionLocator, rateLimitFilter, null);
    }

    @Autowired
    public GatewayFallbackController(
            NacosServiceRegistryManager registryManager,
            NacosDynamicConfigManager configManager,
            NacosDynamicRouteRepository dynamicRouteRepository,
            @Autowired(required = false) RouteDefinitionLocator routeDefinitionLocator,
            GatewayRateLimitGlobalFilter rateLimitFilter,
            @Autowired(required = false) GatewayAdaptiveCircuitBreakerFilter circuitBreakerFilter
    ) {
        this.registryManager = registryManager;
        this.configManager = configManager;
        this.dynamicRouteRepository = dynamicRouteRepository;
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.rateLimitFilter = rateLimitFilter;
        this.circuitBreakerFilter = circuitBreakerFilter;
    }

    /**
     * OpenRTB 2.5 规范竞价降级端点：返回空 seatbid 的合法 BidResponse，避免上游 SSP 判定节点故障而断流
     */
    @RequestMapping(value = "/fallback/rtb", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> rtbFallback() {
        Map<String, Object> noBidResponse = new LinkedHashMap<>();
        noBidResponse.put("id", "gateway-fallback-nobid");
        noBidResponse.put("seatbid", List.of());
        noBidResponse.put("cur", "USD");
        noBidResponse.put("nbr", 2); // OpenRTB No-Bid Reason: 2 = Technical Error / Timeout Fallback
        return Mono.just(ResponseEntity.ok(noBidResponse));
    }

    /**
     * Tracker 追踪数据面降级端点
     */
    @RequestMapping(value = "/fallback/tracker", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> trackerFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "DEGRADED_ACCEPTED",
                "service", "platform-app-tracker",
                "message", "Tracker service temporarily degraded, event buffered at gateway edge",
                "timestamp", Instant.now().toString()
        )));
    }

    /**
     * Admin / Worker 控制面与任务面通用降级端点
     */
    @RequestMapping(value = {"/fallback/admin", "/fallback/worker"}, method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> controlPlaneFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", 503,
                "status", "SERVICE_DEGRADED",
                "message", "Downstream microservice temporarily unavailable, circuit breaker fallback triggered",
                "timestamp", Instant.now().toString()
        )));
    }

    /**
     * 查看网关当前 Nacos 微服务拓扑、生效路由列表与限流运行状态
     */
    @GetMapping("/api/v1/gateway/topology")
    public Mono<ResponseEntity<Map<String, Object>>> getGatewayTopology() {
        Mono<List<RouteDefinition>> routesMono = routeDefinitionLocator != null
                ? routeDefinitionLocator.getRouteDefinitions().collectList()
                : dynamicRouteRepository.getRouteDefinitions().collectList();

        return routesMono.map(routes -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("gateway", "platform-app-gateway");
            result.put("nacosRegistry", registryManager.getRegistryStatus());
            result.put("knownMicroservices", registryManager.getKnownServiceNames());
            result.put("activeRoutesCount", routes.size());
            result.put("activeRoutes", routes);
            result.put("rateLimit", Map.of(
                    "dataplaneQps", rateLimitFilter.getDataplaneQps(),
                    "controlplaneQps", rateLimitFilter.getControlplaneQps(),
                    "totalRejectedRequests", rateLimitFilter.getTotalRejected()
            ));
            if (circuitBreakerFilter != null) {
                result.put("circuitBreakers", circuitBreakerFilter.getCircuitBreakerSnapshots());
                result.put("canaryWeightPercent", circuitBreakerFilter.getCanaryWeightPercent());
            }
            result.put("nacosDynamicConfigKeys", configManager.getAllDynamicProperties().keySet());
            return ResponseEntity.ok(result);
        });
    }

    /**
     * 一键重置网关所有平面的熔断器状态 (OPEN/HALF_OPEN -> CLOSED)
     */
    @PostMapping("/api/v1/gateway/circuit-breakers/reset")
    public Mono<ResponseEntity<Map<String, Object>>> resetCircuitBreakers() {
        if (circuitBreakerFilter != null) {
            circuitBreakerFilter.resetAll();
        }
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "RESET_TO_CLOSED",
                "circuitBreakers", circuitBreakerFilter != null ? circuitBreakerFilter.getCircuitBreakerSnapshots() : Map.of()
        )));
    }

    /**
     * 在线向 Nacos 发布网关动态路由 YAML 并触发实时热更新
     */
    @PostMapping("/api/v1/gateway/routes/publish")
    public Mono<ResponseEntity<Map<String, Object>>> publishDynamicRoutes(
            @RequestParam(value = "group", defaultValue = "AFFILIATE_GROUP") String group,
            @RequestBody String yamlContent
    ) {
        List<RouteDefinition> updated = dynamicRouteRepository.publishDynamicRoutesYaml(group, yamlContent);
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "PUBLISHED_AND_REFRESHED",
                "dataId", NacosDynamicRouteRepository.DEFAULT_ROUTES_DATA_ID,
                "group", group,
                "dynamicRoutesCount", updated.size(),
                "dynamicRoutes", updated
        )));
    }
}
