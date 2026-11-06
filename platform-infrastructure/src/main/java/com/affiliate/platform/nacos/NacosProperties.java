package com.affiliate.platform.nacos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Cloud Alibaba Nacos 兼容配置属性模型 (支持服务注册发现与动态配置中心)
 * <p>
 * 绑定前缀：{@code spring.cloud.nacos}
 */
@Component
@ConfigurationProperties(prefix = "spring.cloud.nacos")
public class NacosProperties {

    /**
     * Nacos 服务端全局地址（支持单节点或逗号分隔集群，如 127.0.0.1:8848）
     */
    private String serverAddr = "127.0.0.1:8848";

    /**
     * Nacos 鉴权用户名
     */
    private String username = "nacos";

    /**
     * Nacos 鉴权密码
     */
    private String password = "nacos";

    /**
     * 服务注册与发现配置
     */
    private Discovery discovery = new Discovery();

    /**
     * 动态配置中心配置
     */
    private Config config = new Config();

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Discovery getDiscovery() {
        return discovery;
    }

    public void setDiscovery(Discovery discovery) {
        this.discovery = discovery;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public static class Discovery {
        private boolean enabled = false;
        private String serverAddr;
        private String namespace = "public";
        private String group = "AFFILIATE_GROUP";
        private String service;
        private String clusterName = "DEFAULT";
        private String ip;
        private int port = -1;
        private double weight = 1.0;
        private boolean ephemeral = true;
        private Map<String, String> metadata = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getServerAddr() { return serverAddr; }
        public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public String getClusterName() { return clusterName; }
        public void setClusterName(String clusterName) { this.clusterName = clusterName; }
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public boolean isEphemeral() { return ephemeral; }
        public void setEphemeral(boolean ephemeral) { this.ephemeral = ephemeral; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }

    public static class Config {
        private boolean enabled = false;
        private String serverAddr;
        private String namespace = "public";
        private String group = "AFFILIATE_GROUP";
        private String prefix;
        private String fileExtension = "yaml";
        private boolean refreshEnabled = true;
        private long timeoutMs = 5000L;
        private List<SharedConfig> sharedConfigs = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getServerAddr() { return serverAddr; }
        public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getFileExtension() { return fileExtension; }
        public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }
        public boolean isRefreshEnabled() { return refreshEnabled; }
        public void setRefreshEnabled(boolean refreshEnabled) { this.refreshEnabled = refreshEnabled; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public List<SharedConfig> getSharedConfigs() { return sharedConfigs; }
        public void setSharedConfigs(List<SharedConfig> sharedConfigs) { this.sharedConfigs = sharedConfigs; }
    }

    public static class SharedConfig {
        private String dataId;
        private String group = "AFFILIATE_GROUP";
        private boolean refresh = true;

        public SharedConfig() {}

        public SharedConfig(String dataId, String group, boolean refresh) {
            this.dataId = dataId;
            this.group = group;
            this.refresh = refresh;
        }

        public String getDataId() { return dataId; }
        public void setDataId(String dataId) { this.dataId = dataId; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public boolean isRefresh() { return refresh; }
        public void setRefresh(boolean refresh) { this.refresh = refresh; }
    }
}
