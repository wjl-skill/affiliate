package com.affiliate.platform.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos 微服务注册与服务发现核心管理器 (Nacos Service Registry & Load-Balanced Discovery Client)
 * <p>
 * 核心能力：
 * 1. 自动将当前微服务节点（Gateway / Tracker / RTB / Admin / Worker / API）注册至 Nacos 注册中心，挂载版本、虚拟线程、Profile 等元数据；
 * 2. 提供带权重随机/轮询负载均衡的服务发现 API ({@link #selectOneHealthyInstance(String)} / {@link #resolveServiceUri(String, String)})；
 * 3. 与 Spring Cloud {@code DiscoveryClient} 及 Spring Cloud Gateway {@code lb://} 协议无缝桥接；
 * 4. 内置本地服务拓扑快照容灾缓存，即使 Nacos Server 短暂抖动也不影响数据面跨服务通信；
 * 5. 支持优雅停机自动反注册实例 ({@link #deregister()})，消除滚动发布期间的 502 流损。
 */
@Component
public class NacosServiceRegistryManager {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistryManager.class);

    public static final List<String> STANDARD_MICROSERVICES = List.of(
            "platform-app-gateway",
            "platform-app-tracker",
            "platform-app-rtb",
            "platform-app-admin",
            "platform-app-worker",
            "platform-api"
    );

    private final NacosProperties nacosProperties;
    private final Environment environment;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final ConcurrentMap<String, List<ServiceInstanceView>> localInstanceCache = new ConcurrentHashMap<>();

    private volatile NamingService namingService;
    private volatile Instance currentInstance;
    private volatile String currentServiceName;
    private volatile String currentGroupName;

    @Autowired
    public NacosServiceRegistryManager(NacosProperties nacosProperties, Environment environment) {
        this.nacosProperties = nacosProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void initAndRegister() {
        NacosProperties.Discovery discovery = nacosProperties.getDiscovery();
        this.currentServiceName = resolveServiceName(discovery);
        this.currentGroupName = (discovery.getGroup() != null && !discovery.getGroup().isBlank())
                ? discovery.getGroup()
                : "AFFILIATE_GROUP";

        Instance self = buildCurrentInstance(discovery);
        this.currentInstance = self;

        // 始终在本地拓扑缓存中记录当前节点，便于本地开发与单测查看
        localInstanceCache.put(currentServiceName, List.of(toView(currentServiceName, self)));

        if (!discovery.isEnabled()) {
            log.info("[NacosDiscovery] Service discovery is disabled (service={}, ip={}:{}). Set spring.cloud.nacos.discovery.enabled=true to register with Nacos.",
                    currentServiceName, self.getIp(), self.getPort());
            return;
        }

        try {
            Properties props = buildNacosClientProperties(discovery.getServerAddr(), discovery.getNamespace());
            this.namingService = NacosFactory.createNamingService(props);
            this.namingService.registerInstance(currentServiceName, currentGroupName, self);
            this.registered.set(true);
            log.info("[NacosDiscovery] Successfully registered microservice [{}] (group={}, cluster={}, addr={}:{}) to Nacos {}",
                    currentServiceName, currentGroupName, self.getClusterName(), self.getIp(), self.getPort(),
                    props.getProperty(PropertyKeyConst.SERVER_ADDR));
        } catch (Exception ex) {
            log.warn("[NacosDiscovery] Failed to connect or register [{}] to Nacos ({}), running with local fallback: {}",
                    currentServiceName, resolveServerAddr(discovery.getServerAddr()), ex.getMessage());
        }
    }

    /**
     * 手动向本地拓扑缓存注册服务实例（用于本地开发联调或单元测试）
     */
    public void registerLocalInstance(String serviceName, String ip, int port, String clusterName, Map<String, String> metadata) {
        if (serviceName == null || serviceName.isBlank()) {
            return;
        }
        ServiceInstanceView view = new ServiceInstanceView(
                serviceName.trim(),
                ip != null ? ip : "127.0.0.1",
                port > 0 ? port : resolveDefaultPort(serviceName.trim()),
                1.0,
                true,
                clusterName != null ? clusterName : "DEFAULT",
                metadata != null ? Map.copyOf(metadata) : Map.of("virtual.threads", "true")
        );
        localInstanceCache.put(serviceName.trim(), List.of(view));
    }

    /**
     * 按服务名负载均衡选取一个健康微服务实例（支持 Nacos 权重随机与本地快照降级）
     */
    public Optional<ServiceInstanceView> selectOneHealthyInstance(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return Optional.empty();
        }
        if (namingService != null && nacosProperties.getDiscovery().isEnabled()) {
            try {
                Instance instance = namingService.selectOneHealthyInstance(serviceName, currentGroupName);
                if (instance != null) {
                    ServiceInstanceView view = toView(serviceName, instance);
                    localInstanceCache.put(serviceName, List.of(view));
                    return Optional.of(view);
                }
            } catch (Exception ex) {
                log.debug("[NacosDiscovery] selectOneHealthyInstance({}) fallback to local cache: {}", serviceName, ex.getMessage());
            }
        }
        List<ServiceInstanceView> cached = localInstanceCache.get(serviceName);
        if (cached != null && !cached.isEmpty()) {
            return Optional.of(cached.get(0));
        }
        return Optional.empty();
    }

    /**
     * 查询指定微服务下的全部健康实例列表（支持 Nacos 实时查询、本地拓扑缓存及标准微服务确定性降级）
     */
    public List<ServiceInstanceView> getHealthyInstances(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return List.of();
        }
        if (namingService != null && nacosProperties.getDiscovery().isEnabled()) {
            try {
                List<Instance> instances = namingService.selectInstances(serviceName, currentGroupName, true);
                if (instances != null && !instances.isEmpty()) {
                    List<ServiceInstanceView> views = instances.stream()
                            .map(inst -> toView(serviceName, inst))
                            .toList();
                    localInstanceCache.put(serviceName, views);
                    return views;
                }
            } catch (Exception ex) {
                log.debug("[NacosDiscovery] getHealthyInstances({}) fallback to local cache: {}", serviceName, ex.getMessage());
            }
        }
        List<ServiceInstanceView> cached = localInstanceCache.get(serviceName);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        if (STANDARD_MICROSERVICES.contains(serviceName)) {
            int defaultPort = resolveDefaultPort(serviceName);
            return List.of(new ServiceInstanceView(
                    serviceName,
                    "127.0.0.1",
                    defaultPort,
                    1.0,
                    true,
                    "LOCAL_FALLBACK",
                    Map.of("virtual.threads", "true", "fallback", "true")
            ));
        }
        return List.of();
    }

    /**
     * 获取当前集群已知的所有微服务名称列表（供 Spring Cloud DiscoveryClient / Gateway 路由发现使用）
     */
    public List<String> getKnownServiceNames() {
        Set<String> names = new LinkedHashSet<>(STANDARD_MICROSERVICES);
        names.addAll(localInstanceCache.keySet());
        if (namingService != null && nacosProperties.getDiscovery().isEnabled()) {
            try {
                ListView<String> services = namingService.getServicesOfServer(1, 200, currentGroupName);
                if (services != null && services.getData() != null) {
                    names.addAll(services.getData());
                }
            } catch (Exception ex) {
                log.debug("[NacosDiscovery] getServicesOfServer fallback to known service set: {}", ex.getMessage());
            }
        }
        return List.copyOf(names);
    }

    /**
     * 将目标微服务名称与相对路径解析为完整 HTTP URI
     */
    public URI resolveServiceUri(String serviceName, String path) {
        String cleanPath = (path != null && path.startsWith("/")) ? path : ("/" + (path != null ? path : ""));
        Optional<ServiceInstanceView> instanceOpt = selectOneHealthyInstance(serviceName);
        if (instanceOpt.isPresent()) {
            ServiceInstanceView inst = instanceOpt.get();
            return URI.create("http://" + inst.ip() + ":" + inst.port() + cleanPath);
        }
        int defaultPort = resolveDefaultPort(serviceName);
        return URI.create("http://127.0.0.1:" + defaultPort + cleanPath);
    }

    public static int resolveDefaultPort(String serviceName) {
        if (serviceName == null) {
            return 8080;
        }
        return switch (serviceName) {
            case "platform-app-gateway" -> 8080;
            case "platform-app-tracker" -> 8081;
            case "platform-app-rtb" -> 8082;
            case "platform-app-admin" -> 8083;
            case "platform-app-worker" -> 8084;
            default -> 8080;
        };
    }

    public Map<String, Object> getRegistryStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        NacosProperties.Discovery d = nacosProperties.getDiscovery();
        status.put("discoveryEnabled", d.isEnabled());
        status.put("registered", registered.get());
        status.put("serverAddr", resolveServerAddr(d.getServerAddr()));
        status.put("namespace", d.getNamespace());
        status.put("group", currentGroupName);
        status.put("serviceName", currentServiceName);
        if (currentInstance != null) {
            status.put("currentInstance", toView(currentServiceName, currentInstance));
        }
        status.put("knownServices", localInstanceCache);
        return status;
    }

    @PreDestroy
    public void deregister() {
        if (namingService != null && registered.compareAndSet(true, false) && currentInstance != null) {
            try {
                namingService.deregisterInstance(currentServiceName, currentGroupName, currentInstance);
                namingService.shutDown();
                log.info("[NacosDiscovery] Gracefully deregistered microservice [{}] ({}:{}) from Nacos",
                        currentServiceName, currentInstance.getIp(), currentInstance.getPort());
            } catch (Exception ex) {
                log.warn("[NacosDiscovery] Error while deregistering microservice [{}]: {}", currentServiceName, ex.getMessage());
            }
        }
    }

    private String resolveServiceName(NacosProperties.Discovery discovery) {
        if (discovery.getService() != null && !discovery.getService().isBlank()) {
            return discovery.getService().trim();
        }
        if (environment != null) {
            String appName = environment.getProperty("spring.application.name");
            if (appName != null && !appName.isBlank()) {
                return appName.trim();
            }
        }
        return "affiliate-platform";
    }

    private Instance buildCurrentInstance(NacosProperties.Discovery discovery) {
        Instance instance = new Instance();
        String ip = discovery.getIp();
        if (ip == null || ip.isBlank()) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                ip = "127.0.0.1";
            }
        }
        int port = discovery.getPort();
        if (port <= 0 && environment != null) {
            port = environment.getProperty("server.port", Integer.class, 8080);
        }
        if (port <= 0) {
            port = 8080;
        }

        instance.setIp(ip);
        instance.setPort(port);
        instance.setWeight(discovery.getWeight() > 0 ? discovery.getWeight() : 1.0);
        instance.setClusterName(discovery.getClusterName() != null ? discovery.getClusterName() : "DEFAULT");
        instance.setEphemeral(discovery.isEphemeral());
        instance.setHealthy(true);

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("version", "0.2.0");
        meta.put("java.version", System.getProperty("java.version", "21"));
        meta.put("virtual.threads", "true");
        meta.put("startup.time", Instant.now().toString());
        if (environment != null && environment.getActiveProfiles().length > 0) {
            meta.put("profiles", String.join(",", environment.getActiveProfiles()));
        }
        if (discovery.getMetadata() != null) {
            meta.putAll(discovery.getMetadata());
        }
        instance.setMetadata(meta);
        return instance;
    }

    private Properties buildNacosClientProperties(String specificServerAddr, String namespace) {
        Properties props = new Properties();
        props.setProperty(PropertyKeyConst.SERVER_ADDR, resolveServerAddr(specificServerAddr));
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

    private String resolveServerAddr(String specificAddr) {
        if (specificAddr != null && !specificAddr.isBlank()) {
            return specificAddr.trim();
        }
        return nacosProperties.getServerAddr() != null ? nacosProperties.getServerAddr().trim() : "127.0.0.1:8848";
    }

    private static ServiceInstanceView toView(String serviceName, Instance inst) {
        return new ServiceInstanceView(
                serviceName,
                inst.getIp(),
                inst.getPort(),
                inst.getWeight(),
                inst.isHealthy(),
                inst.getClusterName(),
                inst.getMetadata() != null ? Map.copyOf(inst.getMetadata()) : Map.of()
        );
    }

    public record ServiceInstanceView(
            String serviceName,
            String ip,
            int port,
            double weight,
            boolean healthy,
            String clusterName,
            Map<String, String> metadata
    ) {}
}
