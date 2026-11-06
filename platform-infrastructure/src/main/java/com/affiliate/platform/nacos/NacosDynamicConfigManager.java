package com.affiliate.platform.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nacos 动态配置中心热更新管理器 (Nacos Dynamic Config Center Manager)
 * <p>
 * 核心能力：
 * 1. 启动时自动拉取共享配置 (`shared-configs`，如 `affiliate-common.yaml`) 与当前微服务专属配置
 *    (`${spring.application.name}-${profile}.yaml`)；
 * 2. 将远端 YAML/Properties 配置解析并注入 Spring {@link ConfigurableEnvironment} 最高优先级属性源 (`nacos-dynamic-config`)；
 * 3. 自动为开启 `refresh=true` 的 DataId 注册长轮询监听器，配置变更秒级热更新并广播 {@link NacosConfigRefreshEvent}；
 * 4. 支持在线发布/更新 Nacos 配置并在离线环境下平滑降级为本地内存快照。
 */
@Component
public class NacosDynamicConfigManager {

    private static final Logger log = LoggerFactory.getLogger(NacosDynamicConfigManager.class);
    public static final String NACOS_PROPERTY_SOURCE_NAME = "nacos-dynamic-config";

    private final NacosProperties nacosProperties;
    private final ConfigurableEnvironment environment;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentMap<String, Object> dynamicProperties = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> rawConfigStore = new ConcurrentHashMap<>();
    private final Executor listenerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile ConfigService configService;

    @Autowired
    public NacosDynamicConfigManager(
            NacosProperties nacosProperties,
            @Autowired(required = false) ConfigurableEnvironment environment,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher
    ) {
        this.nacosProperties = nacosProperties;
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void initConfigCenter() {
        if (environment != null) {
            environment.getPropertySources().addFirst(new MapPropertySource(NACOS_PROPERTY_SOURCE_NAME, dynamicProperties));
        }

        NacosProperties.Config cfg = nacosProperties.getConfig();
        if (!cfg.isEnabled()) {
            log.info("[NacosConfig] Dynamic config center is disabled. Set spring.cloud.nacos.config.enabled=true to enable Nacos config sync.");
            return;
        }

        try {
            Properties props = buildNacosClientProperties(cfg.getServerAddr(), cfg.getNamespace());
            this.configService = NacosFactory.createConfigService(props);

            String defaultGroup = (cfg.getGroup() != null && !cfg.getGroup().isBlank()) ? cfg.getGroup() : "AFFILIATE_GROUP";
            long timeout = cfg.getTimeoutMs() > 0 ? cfg.getTimeoutMs() : 5000L;

            // 1. 依次加载共享配置 (shared-configs)
            if (cfg.getSharedConfigs() != null) {
                for (NacosProperties.SharedConfig shared : cfg.getSharedConfigs()) {
                    if (shared.getDataId() == null || shared.getDataId().isBlank()) {
                        continue;
                    }
                    String grp = (shared.getGroup() != null && !shared.getGroup().isBlank()) ? shared.getGroup() : defaultGroup;
                    pullAndBindDataId(shared.getDataId().trim(), grp, timeout, cfg.isRefreshEnabled() && shared.isRefresh());
                }
            }

            // 2. 加载当前微服务主配置与 Profile 专属配置
            String prefix = resolvePrefix(cfg);
            String ext = (cfg.getFileExtension() != null && !cfg.getFileExtension().isBlank()) ? cfg.getFileExtension().trim() : "yaml";
            pullAndBindDataId(prefix + "." + ext, defaultGroup, timeout, cfg.isRefreshEnabled());

            if (environment != null) {
                for (String profile : environment.getActiveProfiles()) {
                    if (profile != null && !profile.isBlank()) {
                        pullAndBindDataId(prefix + "-" + profile.trim() + "." + ext, defaultGroup, timeout, cfg.isRefreshEnabled());
                    }
                }
            }

            log.info("[NacosConfig] Initialized Nacos config center ({} dynamic keys active)", dynamicProperties.size());
        } catch (Exception ex) {
            log.warn("[NacosConfig] Failed to initialize Nacos ConfigService, falling back to local YAML configs: {}", ex.getMessage());
        }
    }

    /**
     * 应用并热更新指定 DataId 的 YAML / Properties 配置文本（同时支持 Nacos 远端回调与本地 API 触发）
     */
    public Map<String, Object> applyConfigContent(String dataId, String group, String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        String effectiveGroup = (group != null && !group.isBlank()) ? group : "AFFILIATE_GROUP";
        rawConfigStore.put(effectiveGroup + ":" + dataId, content);

        Map<String, Object> parsed = parseConfigToMap(dataId, content);
        if (!parsed.isEmpty()) {
            dynamicProperties.putAll(parsed);
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new NacosConfigRefreshEvent(this, dataId, effectiveGroup, parsed));
            }
            log.info("[NacosConfig] Applied {} dynamic properties from DataId [{}] (group={})",
                    parsed.size(), dataId, effectiveGroup);
        }
        return parsed;
    }

    /**
     * 发布配置至 Nacos 配置中心并同步刷新本地动态属性源
     */
    public boolean publishConfig(String dataId, String group, String content) {
        if (dataId == null || dataId.isBlank() || content == null) {
            return false;
        }
        String effectiveGroup = (group != null && !group.isBlank()) ? group : "AFFILIATE_GROUP";
        applyConfigContent(dataId, effectiveGroup, content);

        if (configService != null && nacosProperties.getConfig().isEnabled()) {
            try {
                String type = dataId.endsWith(".properties") ? ConfigType.PROPERTIES.getType() : ConfigType.YAML.getType();
                return configService.publishConfig(dataId, effectiveGroup, content, type);
            } catch (Exception ex) {
                log.warn("[NacosConfig] Remote publishConfig failed for {}, kept in local dynamic store: {}", dataId, ex.getMessage());
            }
        }
        return true;
    }

    public Object getDynamicProperty(String key) {
        return dynamicProperties.get(key);
    }

    public Map<String, Object> getAllDynamicProperties() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(dynamicProperties));
    }

    @PreDestroy
    public void shutdown() {
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (Exception ignored) {
            }
        }
    }

    private void pullAndBindDataId(String dataId, String group, long timeoutMs, boolean registerListener) {
        if (configService == null) {
            return;
        }
        try {
            String content = configService.getConfig(dataId, group, timeoutMs);
            if (content != null && !content.isBlank()) {
                applyConfigContent(dataId, group, content);
            }
            if (registerListener) {
                configService.addListener(dataId, group, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return listenerExecutor;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        log.info("[NacosConfig] Received hot-reload push from Nacos for DataId [{}] (group={})", dataId, group);
                        applyConfigContent(dataId, group, configInfo);
                    }
                });
            }
        } catch (Exception ex) {
            log.debug("[NacosConfig] Pull DataId [{}] (group={}) skipped: {}", dataId, group, ex.getMessage());
        }
    }

    private Map<String, Object> parseConfigToMap(String dataId, String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Properties props;
            if (dataId != null && dataId.endsWith(".properties")) {
                props = new Properties();
                props.load(new StringReader(content));
            } else {
                YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
                yamlFactory.setResources(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                props = yamlFactory.getObject();
            }
            if (props != null) {
                props.forEach((k, v) -> {
                    if (k != null && v != null) {
                        result.put(String.valueOf(k), String.valueOf(v));
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("[NacosConfig] Failed to parse config content for DataId [{}]: {}", dataId, ex.getMessage());
        }
        return result;
    }

    private String resolvePrefix(NacosProperties.Config cfg) {
        if (cfg.getPrefix() != null && !cfg.getPrefix().isBlank()) {
            return cfg.getPrefix().trim();
        }
        if (environment != null) {
            String appName = environment.getProperty("spring.application.name");
            if (appName != null && !appName.isBlank()) {
                return appName.trim();
            }
        }
        return "affiliate-platform";
    }

    private Properties buildNacosClientProperties(String specificServerAddr, String namespace) {
        Properties props = new Properties();
        String addr = (specificServerAddr != null && !specificServerAddr.isBlank())
                ? specificServerAddr.trim()
                : (nacosProperties.getServerAddr() != null ? nacosProperties.getServerAddr().trim() : "127.0.0.1:8848");
        props.setProperty(PropertyKeyConst.SERVER_ADDR, addr);
        if (namespace != null && !namespace.isBlank() && !"public".equalsIgnoreCase(namespace)) {
            props.setProperty(PropertyKeyConst.NAMESPACE, namespace.trim());
        }
        if (nacosProperties.getUsername() != null && !nacosProperties.getUsername().isBlank()) {
            props.setProperty(PropertyKeyConst.USERNAME, nacosProperties.getUsername());
        }
        if (nacosProperties.getPassword() != null && !nacosProperties.getPassword().isBlank()) {
            props.setProperty(PropertyKeyConst.PASSWORD, nacosProperties.getPassword());
        }
        return props;
    }
}
