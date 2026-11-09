package com.affiliate.platform.gateway.route;

import com.affiliate.platform.nacos.NacosConfigRefreshEvent;
import com.affiliate.platform.nacos.NacosDynamicConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 Nacos 动态配置中心的 Spring Cloud Gateway 动态路由仓储 (Nacos Dynamic Route Repository)
 * <p>
 * 核心机制：
 * 1. 实现 Spring Cloud Gateway 标准 {@link RouteDefinitionRepository} 接口；
 * 2. 监听 {@link NacosConfigRefreshEvent}（如 DataId {@code affiliate-gateway-routes.yaml} 或包含
 *    {@code gateway.dynamic-routes} 前缀的配置变更）；
 * 3. 解析动态路由规则（{@code id}, {@code uri}, {@code predicates}, {@code filters}, {@code order}）并原子替换内存路由表，
 *    随后广播 {@link RefreshRoutesEvent} 触发网关路由表零停机秒级热更新。
 */
@Component
public class NacosDynamicRouteRepository implements RouteDefinitionRepository {

    private static final Logger log = LoggerFactory.getLogger(NacosDynamicRouteRepository.class);
    public static final String DEFAULT_ROUTES_DATA_ID = "affiliate-gateway-routes.yaml";

    private final ConcurrentMap<String, RouteDefinition> dynamicRoutes = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;
    private final NacosDynamicConfigManager configManager;

    @Autowired
    public NacosDynamicRouteRepository(
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) NacosDynamicConfigManager configManager
    ) {
        this.eventPublisher = eventPublisher;
        this.configManager = configManager;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(new ArrayList<>(dynamicRoutes.values()));
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(r -> {
            if (r == null || r.getId() == null || r.getId().isBlank()) {
                return Mono.error(new IllegalArgumentException("RouteDefinition id must not be empty"));
            }
            dynamicRoutes.put(r.getId(), r);
            publishRefreshEvent();
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            if (id != null && dynamicRoutes.remove(id) != null) {
                publishRefreshEvent();
            }
            return Mono.empty();
        });
    }

    /**
     * 监听 Nacos 配置热更新事件，当检测到网关动态路由配置变更时自动重建路由表并触发 RefreshRoutesEvent
     */
    @EventListener
    public void onNacosConfigRefresh(NacosConfigRefreshEvent event) {
        if (event == null || event.getUpdatedProperties() == null) {
            return;
        }
        Map<String, Object> props = event.getUpdatedProperties();
        boolean hasGatewayRouteKeys = DEFAULT_ROUTES_DATA_ID.equalsIgnoreCase(event.getDataId())
                || props.keySet().stream().anyMatch(k -> k.startsWith("gateway.dynamic-routes"));

        if (!hasGatewayRouteKeys) {
            return;
        }

        List<RouteDefinition> parsed = parseRoutesFromProperties(props);
        if (!parsed.isEmpty()) {
            for (RouteDefinition def : parsed) {
                dynamicRoutes.put(def.getId(), def);
            }
            log.info("[NacosGatewayRoutes] Hot-reloaded {} dynamic routes from Nacos DataId [{}]",
                    parsed.size(), event.getDataId());
            publishRefreshEvent();
        }
    }

    /**
     * 通过 YAML 内容直接发布并刷新网关动态路由
     */
    public List<RouteDefinition> publishDynamicRoutesYaml(String group, String yamlContent) {
        if (configManager != null) {
            configManager.publishConfig(DEFAULT_ROUTES_DATA_ID, group, yamlContent);
        }
        return List.copyOf(dynamicRoutes.values());
    }

    public Map<String, RouteDefinition> getDynamicRouteMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(dynamicRoutes));
    }

    /**
     * 从扁平化属性 Map 中解析 gateway.dynamic-routes[i].* 结构
     */
    public List<RouteDefinition> parseRoutesFromProperties(Map<String, Object> props) {
        List<RouteDefinition> definitions = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            String base = "gateway.dynamic-routes[" + i + "]";
            String id = asString(props.get(base + ".id"));
            String uriStr = asString(props.get(base + ".uri"));
            if (id == null || id.isBlank() || uriStr == null || uriStr.isBlank()) {
                if (i == 0) continue;
                break;
            }

            RouteDefinition def = new RouteDefinition();
            def.setId(id.trim());
            def.setUri(URI.create(uriStr.trim()));

            String orderStr = asString(props.get(base + ".order"));
            if (orderStr != null && !orderStr.isBlank()) {
                try {
                    def.setOrder(Integer.parseInt(orderStr.trim()));
                } catch (NumberFormatException ignored) {
                }
            }

            List<PredicateDefinition> predicates = new ArrayList<>();
            for (int p = 0; p < 16; p++) {
                String predText = asString(props.get(base + ".predicates[" + p + "]"));
                if (predText == null || predText.isBlank()) {
                    break;
                }
                predicates.add(new PredicateDefinition(predText.trim()));
            }
            def.setPredicates(predicates);

            List<FilterDefinition> filters = new ArrayList<>();
            for (int f = 0; f < 16; f++) {
                String filterText = asString(props.get(base + ".filters[" + f + "]"));
                if (filterText == null || filterText.isBlank()) {
                    break;
                }
                filters.add(new FilterDefinition(filterText.trim()));
            }
            def.setFilters(filters);

            definitions.add(def);
        }
        return definitions;
    }

    private void publishRefreshEvent() {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        }
    }

    private static String asString(Object val) {
        return val != null ? String.valueOf(val) : null;
    }
}
