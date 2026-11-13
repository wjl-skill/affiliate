package com.affiliate.platform.nacos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 微服务物理运行面边界强隔离守卫过滤器 (Microservice Plane Endpoint Isolation Filter)
 * <p>
 * 解决多模块共享组件扫描（{@code scanBasePackages = "com.affiliate.platform"}）下，
 * 数据面（Tracker / RTB）、异步任务面（Worker）与运营控制面（Admin）Controller 互相暴露的安全隐患：
 * <ul>
 *     <li><b>TRACKER (8081)</b>：仅暴露点击重定向、S2S Postback 与 SKAN 归因端点，严禁通过数据面端口访问管理后台 API；</li>
 *     <li><b>RTB (8082)</b>：仅暴露 OpenRTB 2.5 竞价、胜出/曝光/点击打点及 ADX/DSP/SSP 协议端点；</li>
 *     <li><b>WORKER (8084)</b>：仅暴露异步补量调度与事务发件箱死信重放运维端点；</li>
 *     <li><b>ADMIN (8083)</b>：仅暴露 {@code /api/v1/**} 控制面接口，拒绝高频公网竞价/点击流量直接打入管理节点；</li>
 *     <li><b>ALL</b>：单体兼容模式或全功能联调模式，放行全部端点。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MicroservicePlaneEndpointFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MicroservicePlaneEndpointFilter.class);

    public enum Plane {
        TRACKER,
        RTB,
        ADMIN,
        WORKER,
        ALL
    }

    private static final List<String> TRACKER_ALLOWED_PREFIXES = List.of(
            "/affiliate/click",
            "/affiliate/postback",
            "/affiliate/skan",
            "/clk",
            "/s2s",
            "/api/v1/affiliate/click",
            "/api/v1/affiliate/postback",
            "/api/v1/affiliate/skan",
            "/api/v1/skan",
            "/actuator"
    );

    private static final List<String> RTB_ALLOWED_PREFIXES = List.of(
            "/rtb",
            "/api/v1/adx",
            "/api/v1/dsp",
            "/api/v1/ssp",
            "/actuator"
    );

    private static final List<String> WORKER_ALLOWED_PREFIXES = List.of(
            "/api/v1/ops/outbox",
            "/api/v1/outbox",
            "/api/v1/affiliate/offers/supplement",
            "/api/v1/affiliate/supplement",
            "/actuator"
    );

    private static final List<String> ADMIN_FORBIDDEN_DATAPLANE_PREFIXES = List.of(
            "/rtb",
            "/affiliate/click",
            "/affiliate/postback",
            "/clk",
            "/s2s"
    );

    @Value("${spring.application.name:affiliate-platform}")
    private String applicationName = "affiliate-platform";

    @Value("${app.microservice.plane:AUTO}")
    private volatile String configuredPlane = "AUTO";

    @Value("${app.microservice.strict-isolation:true}")
    private volatile boolean strictIsolation = true;

    @EventListener
    public void onNacosConfigRefresh(NacosConfigRefreshEvent event) {
        if (event == null || event.getUpdatedProperties() == null) {
            return;
        }
        Map<String, Object> props = event.getUpdatedProperties();
        if (props.containsKey("app.microservice.plane")) {
            this.configuredPlane = String.valueOf(props.get("app.microservice.plane")).trim().toUpperCase();
            log.info("[PlaneIsolation] Hot-updated app.microservice.plane={}", this.configuredPlane);
        }
        if (props.containsKey("app.microservice.strict-isolation")) {
            this.strictIsolation = Boolean.parseBoolean(String.valueOf(props.get("app.microservice.strict-isolation")));
            log.info("[PlaneIsolation] Hot-updated app.microservice.strict-isolation={}", this.strictIsolation);
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Plane activePlane = resolveActivePlane();
        response.setHeader("X-Microservice-Plane", activePlane.name());

        if (!strictIsolation || activePlane == Plane.ALL) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (!isEndpointAllowedForPlane(activePlane, path)) {
            log.warn("[PlaneIsolation] Blocked cross-plane access on plane [{}]: method={}, path={}",
                    activePlane, request.getMethod(), path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            String body = String.format(
                    "{\"code\":403,\"error\":\"PLANE_ISOLATION_VIOLATION\",\"plane\":\"%s\",\"path\":\"%s\",\"message\":\"Endpoint is not exposed on %s microservice plane\"}",
                    activePlane.name(), path, activePlane.name()
            );
            response.getWriter().write(body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    public Plane resolveActivePlane() {
        if (configuredPlane != null && !configuredPlane.isBlank() && !"AUTO".equalsIgnoreCase(configuredPlane)) {
            try {
                return Plane.valueOf(configuredPlane.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (applicationName == null) {
            return Plane.ALL;
        }
        return switch (applicationName.trim().toLowerCase()) {
            case "platform-app-tracker" -> Plane.TRACKER;
            case "platform-app-rtb" -> Plane.RTB;
            case "platform-app-admin" -> Plane.ADMIN;
            case "platform-app-worker" -> Plane.WORKER;
            default -> Plane.ALL;
        };
    }

    public static boolean isEndpointAllowedForPlane(Plane plane, String path) {
        if (plane == null || plane == Plane.ALL || path == null || path.isBlank()) {
            return true;
        }
        return switch (plane) {
            case TRACKER -> matchesAnyPrefix(path, TRACKER_ALLOWED_PREFIXES);
            case RTB -> matchesAnyPrefix(path, RTB_ALLOWED_PREFIXES);
            case WORKER -> matchesAnyPrefix(path, WORKER_ALLOWED_PREFIXES);
            case ADMIN -> !matchesAnyPrefix(path, ADMIN_FORBIDDEN_DATAPLANE_PREFIXES);
            case ALL -> true;
        };
    }

    private static boolean matchesAnyPrefix(String path, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setConfiguredPlane(String configuredPlane) {
        this.configuredPlane = configuredPlane;
    }

    public void setStrictIsolation(boolean strictIsolation) {
        this.strictIsolation = strictIsolation;
    }
}
