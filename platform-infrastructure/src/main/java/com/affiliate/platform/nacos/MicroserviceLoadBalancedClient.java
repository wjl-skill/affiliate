package com.affiliate.platform.nacos;

import com.affiliate.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 跨微服务负载均衡与容灾 RPC 客户端 (Spring Cloud Discovery Load-Balanced HTTP Client)
 * <p>
 * 核心能力：
 * 1. 基于 Spring Cloud {@link DiscoveryClient} 与 {@link NacosServiceRegistryManager} 动态解析目标微服务健康实例；
 * 2. 支持加权/随机负载均衡选路与自动故障转移重试（单节点连接失败时自动切换备选健康节点）；
 * 3. 自动透传 {@code X-Tenant-Id} 与 {@code X-Trace-Id} 上下文，底层采用 Java 21 虚拟线程非阻塞执行。
 */
@Component
public class MicroserviceLoadBalancedClient {

    private static final Logger log = LoggerFactory.getLogger(MicroserviceLoadBalancedClient.class);

    private final DiscoveryClient discoveryClient;
    private final NacosServiceRegistryManager registryManager;
    private final HttpClient httpClient;

    public MicroserviceLoadBalancedClient(
            DiscoveryClient discoveryClient,
            NacosServiceRegistryManager registryManager
    ) {
        this.discoveryClient = discoveryClient;
        this.registryManager = registryManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1500))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    /**
     * 解析目标微服务的负载均衡 URI（支持 lb://service-name/path 或直接传入 serviceName + path）
     */
    public URI chooseInstanceUri(String serviceName, String path) {
        String cleanPath = (path != null && path.startsWith("/")) ? path : ("/" + (path != null ? path : ""));
        if (discoveryClient != null) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            if (instances != null && !instances.isEmpty()) {
                ServiceInstance chosen = instances.size() == 1
                        ? instances.get(0)
                        : instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
                return URI.create(chosen.getUri().toString() + cleanPath);
            }
        }
        return registryManager.resolveServiceUri(serviceName, cleanPath);
    }

    /**
     * 向目标微服务发起带负载均衡与跨节点故障转移的 GET 请求
     */
    public HttpResponse<String> get(String serviceName, String path) {
        return executeWithFailover(serviceName, path, "GET", null);
    }

    /**
     * 向目标微服务发起带负载均衡与跨节点故障转移的 POST JSON 请求
     */
    public HttpResponse<String> postJson(String serviceName, String path, String jsonBody) {
        return executeWithFailover(serviceName, path, "POST", jsonBody);
    }

    private HttpResponse<String> executeWithFailover(String serviceName, String path, String method, String jsonBody) {
        String cleanPath = (path != null && path.startsWith("/")) ? path : ("/" + (path != null ? path : ""));
        List<ServiceInstance> instances = discoveryClient != null ? discoveryClient.getInstances(serviceName) : List.of();
        int maxAttempts = Math.min(Math.max(1, instances.size()), 2);

        Exception lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            URI targetUri;
            if (!instances.isEmpty()) {
                ServiceInstance inst = instances.get((attempt) % instances.size());
                targetUri = URI.create(inst.getUri().toString() + cleanPath);
            } else {
                targetUri = registryManager.resolveServiceUri(serviceName, cleanPath);
            }

            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(targetUri)
                        .timeout(Duration.ofSeconds(3))
                        .header("Accept", "application/json")
                        .header("X-Trace-Id", resolveTraceId())
                        .header("X-Tenant-Id", resolveTenantId());

                if ("POST".equalsIgnoreCase(method)) {
                    builder.header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}", StandardCharsets.UTF_8));
                } else {
                    builder.GET();
                }

                return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                lastException = ex;
                log.warn("[MicroserviceRpc] Attempt {}/{} to {} ({}) failed: {}",
                        attempt + 1, maxAttempts, serviceName, targetUri, ex.getMessage());
            }
        }
        throw new IllegalStateException("Microservice call failed for " + serviceName + cleanPath, lastException);
    }

    private static String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    private static String resolveTenantId() {
        String tenant = TenantContext.get();
        return (tenant != null && !tenant.isBlank()) ? tenant : "public";
    }
}
