package com.affiliate.platform.gateway.config;

import com.affiliate.platform.nacos.NacosSpringCloudDiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

/**
 * 将项目现有的 Nacos 服务注册发现体系桥接为 Spring Cloud Gateway 响应式 {@link ReactiveDiscoveryClient}
 * <p>
 * 使 {@code ReactiveLoadBalancerClientFilter} 与 {@code lb://platform-app-*} 协议能够直接基于
 * {@link NacosSpringCloudDiscoveryClient} 进行异步非阻塞实例发现与负载均衡。
 */
@Configuration
public class NacosReactiveDiscoveryClientConfiguration {

    @Bean
    @Primary
    public ReactiveDiscoveryClient nacosReactiveDiscoveryClient(NacosSpringCloudDiscoveryClient discoveryClient) {
        return new ReactiveDiscoveryClient() {
            @Override
            public String description() {
                return "Affiliate Platform Nacos ReactiveDiscoveryClient";
            }

            @Override
            public Flux<ServiceInstance> getInstances(String serviceId) {
                return Flux.defer(() -> Flux.fromIterable(discoveryClient.getInstances(serviceId)));
            }

            @Override
            public Flux<String> getServices() {
                return Flux.defer(() -> Flux.fromIterable(discoveryClient.getServices()));
            }
        };
    }
}
