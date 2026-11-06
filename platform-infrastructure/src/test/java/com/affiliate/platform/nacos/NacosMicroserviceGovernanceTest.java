package com.affiliate.platform.nacos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NacosMicroserviceGovernanceTest {

    @Test
    @DisplayName("1. 验证微服务注册发现管理器的本地容灾快照与 URI 负载均衡解析")
    void testServiceRegistrationAndUriResolution() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.application.name", "platform-app-tracker")
                .withProperty("server.port", "8081");

        NacosProperties props = new NacosProperties();
        props.setServerAddr("127.0.0.1:8848");
        props.getDiscovery().setEnabled(false);
        props.getDiscovery().setGroup("AFFILIATE_GROUP");
        props.getDiscovery().setClusterName("DATAPLANE");
        props.getDiscovery().setIp("10.10.1.25");
        props.getDiscovery().setPort(8081);

        NacosServiceRegistryManager registry = new NacosServiceRegistryManager(props, env);
        registry.initAndRegister();

        List<NacosServiceRegistryManager.ServiceInstanceView> instances =
                registry.getHealthyInstances("platform-app-tracker");
        assertEquals(1, instances.size());
        assertEquals("10.10.1.25", instances.get(0).ip());
        assertEquals(8081, instances.get(0).port());
        assertEquals("DATAPLANE", instances.get(0).clusterName());
        assertEquals("true", instances.get(0).metadata().get("virtual.threads"));

        URI resolvedUri = registry.resolveServiceUri("platform-app-tracker", "/affiliate/click");
        assertEquals("http://10.10.1.25:8081/affiliate/click", resolvedUri.toString());

        URI fallbackRtbUri = registry.resolveServiceUri("platform-app-rtb", "/openrtb2/bid");
        assertEquals("http://127.0.0.1:8082/openrtb2/bid", fallbackRtbUri.toString());
    }

    @Test
    @DisplayName("2. 验证 Nacos 动态配置中心 YAML 解析、Environment 注入与热更新事件广播")
    void testDynamicConfigHotReloadAndEnvironmentBinding() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.application.name", "platform-app-tracker");

        NacosProperties props = new NacosProperties();
        props.getConfig().setEnabled(false);

        AtomicReference<NacosConfigRefreshEvent> receivedEvent = new AtomicReference<>();
        NacosDynamicConfigManager configManager = new NacosDynamicConfigManager(
                props,
                env,
                event -> {
                    if (event instanceof NacosConfigRefreshEvent refreshEvent) {
                        receivedEvent.set(refreshEvent);
                    }
                }
        );
        configManager.initConfigCenter();

        String yamlContent = """
                app:
                  bandit:
                    ab-control-ratio: 0.15
                    alpha: 0.35
                  antifraud:
                    min-ctit-seconds: 8
                """;

        boolean published = configManager.publishConfig("affiliate-common.yaml", "AFFILIATE_GROUP", yamlContent);
        assertTrue(published);

        // 验证 Spring Environment 最高优先级属性源已实时生效
        assertEquals("0.15", env.getProperty("app.bandit.ab-control-ratio"));
        assertEquals("0.35", env.getProperty("app.bandit.alpha"));
        assertEquals("8", env.getProperty("app.antifraud.min-ctit-seconds"));

        // 验证 NacosConfigRefreshEvent 热更新事件已触发
        assertNotNull(receivedEvent.get());
        assertEquals("affiliate-common.yaml", receivedEvent.get().getDataId());
        assertEquals("AFFILIATE_GROUP", receivedEvent.get().getGroup());
        Map<String, Object> updated = receivedEvent.get().getUpdatedProperties();
        assertEquals("0.15", updated.get("app.bandit.ab-control-ratio"));
    }
}
