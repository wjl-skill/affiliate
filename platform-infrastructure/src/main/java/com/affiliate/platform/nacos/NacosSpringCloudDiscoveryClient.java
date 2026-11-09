package com.affiliate.platform.nacos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于现有 {@link NacosServiceRegistryManager} 封装的标准 Spring Cloud {@link DiscoveryClient} 实现
 * <p>
 * 作用：
 * 1. 将项目统一的 Nacos 注册与本地容灾快照能力无缝接入 Spring Cloud 生态（LoadBalancer / Gateway / RestClient）；
 * 2. 将 Nacos 节点权重、集群名、健康状态等扩展属性注入 {@link ServiceInstance#getMetadata()}；
 * 3. 在 Nacos Server 启用或本地离线模式下均可解析 {@code lb://platform-app-*} 负载均衡路由。
 */
@Component
public class NacosSpringCloudDiscoveryClient implements DiscoveryClient {

    private final NacosServiceRegistryManager registryManager;

    @Autowired
    public NacosSpringCloudDiscoveryClient(NacosServiceRegistryManager registryManager) {
        this.registryManager = registryManager;
    }

    @Override
    public String description() {
        return "Affiliate Platform Nacos Spring Cloud DiscoveryClient";
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        if (serviceId == null || serviceId.isBlank() || registryManager == null) {
            return List.of();
        }
        List<NacosServiceRegistryManager.ServiceInstanceView> healthyViews = registryManager.getHealthyInstances(serviceId);
        return healthyViews.stream()
                .map(view -> toSpringCloudInstance(serviceId, view))
                .toList();
    }

    @Override
    public List<String> getServices() {
        if (registryManager == null) {
            return List.of();
        }
        return registryManager.getKnownServiceNames();
    }

    public static ServiceInstance toSpringCloudInstance(
            String serviceId,
            NacosServiceRegistryManager.ServiceInstanceView view
    ) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (view.metadata() != null) {
            metadata.putAll(view.metadata());
        }
        metadata.put("nacos.weight", String.valueOf(view.weight()));
        metadata.put("nacos.cluster", view.clusterName() != null ? view.clusterName() : "DEFAULT");
        metadata.put("nacos.healthy", String.valueOf(view.healthy()));

        String instanceId = serviceId + "#" + view.ip() + "#" + view.port();
        boolean secure = "true".equalsIgnoreCase(metadata.get("secure"));

        return new DefaultServiceInstance(
                instanceId,
                serviceId,
                view.ip(),
                view.port(),
                secure,
                metadata
        );
    }
}
