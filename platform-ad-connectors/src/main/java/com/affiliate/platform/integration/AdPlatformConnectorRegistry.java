package com.affiliate.platform.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用多广告平台连接器与 OAuth 客户端策略注册表 (Ad Platform Connector Registry)
 * <p>
 * 提供平台别名归一化（如 TT -> TIKTOK, FB/META -> FACEBOOK, GOOGLE -> GOOGLE_ADS）与驱动实例查找。
 */
@Component
public class AdPlatformConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(AdPlatformConnectorRegistry.class);

    private final Map<String, AdPlatformConnector> connectorMap = new ConcurrentHashMap<>();
    private final Map<String, AdPlatformOAuthClient> oAuthClientMap = new ConcurrentHashMap<>();

    @Autowired
    public AdPlatformConnectorRegistry(
            List<AdPlatformConnector> connectors,
            @Autowired(required = false) List<AdPlatformOAuthClient> oAuthClients) {

        if (connectors != null) {
            for (AdPlatformConnector connector : connectors) {
                registerConnector(connector);
            }
        }

        if (oAuthClients != null) {
            for (AdPlatformOAuthClient client : oAuthClients) {
                registerOAuthClient(client);
            }
        }

        log.info("[AdPlatformConnectorRegistry] Initialized with {} connectors and {} OAuth clients. Supported platforms: {}",
                connectors != null ? connectors.size() : 0,
                oAuthClients != null ? oAuthClients.size() : 0,
                getSupportedPlatforms());
    }

    public void registerConnector(AdPlatformConnector connector) {
        if (connector == null || connector.provider() == null) return;
        String canonical = normalizePlatform(connector.provider());
        connectorMap.put(canonical, connector);

        // 注册常见别名
        for (String alias : getAliasesFor(canonical)) {
            connectorMap.put(alias, connector);
        }
    }

    public void registerOAuthClient(AdPlatformOAuthClient client) {
        if (client == null || client.platform() == null) return;
        String canonical = normalizePlatform(client.platform());
        oAuthClientMap.put(canonical, client);

        for (String alias : getAliasesFor(canonical)) {
            oAuthClientMap.put(alias, client);
        }
    }

    /**
     * 获取指定平台的连接器实现
     *
     * @param platform 平台标识或别名（如 "tt", "fb", "meta", "google"）
     * @return 对应的连接器实例
     */
    public AdPlatformConnector getConnector(String platform) {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("Platform must not be blank");
        }
        String key = normalizePlatform(platform);
        AdPlatformConnector connector = connectorMap.get(key);
        if (connector == null) {
            throw new IllegalArgumentException("Unsupported ad platform connector: " + platform
                    + ". Available: " + getSupportedPlatforms());
        }
        return connector;
    }

    /**
     * 检查是否支持该平台连接器
     */
    public boolean hasConnector(String platform) {
        if (platform == null || platform.isBlank()) return false;
        return connectorMap.containsKey(normalizePlatform(platform));
    }

    /**
     * 获取指定平台的 OAuth 客户端
     */
    public AdPlatformOAuthClient getOAuthClient(String platform) {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("Platform must not be blank");
        }
        String key = normalizePlatform(platform);
        AdPlatformOAuthClient client = oAuthClientMap.get(key);
        if (client == null) {
            throw new IllegalArgumentException("No OAuth client available for platform: " + platform);
        }
        return client;
    }

    /**
     * 获取当前支持的所有标准平台标识列表
     */
    public Set<String> getSupportedPlatforms() {
        Set<String> set = new TreeSet<>();
        for (AdPlatformConnector c : connectorMap.values()) {
            set.add(normalizePlatform(c.provider()));
        }
        return Collections.unmodifiableSet(set);
    }

    public static String normalizePlatform(String platform) {
        if (platform == null) return "";
        String clean = platform.trim().toUpperCase().replace("-", "_");
        return switch (clean) {
            case "GOOGLE", "GOOGLE_ADS", "GADS" -> "GOOGLE_ADS";
            case "TT", "TIKTOK", "TIKTOK_ADS" -> "TIKTOK";
            case "FB", "FACEBOOK", "META", "META_ADS" -> "FACEBOOK";
            case "GAM", "GOOGLE_GAM" -> "GAM";
            default -> clean;
        };
    }

    private static List<String> getAliasesFor(String canonical) {
        return switch (canonical) {
            case "GOOGLE_ADS" -> List.of("GOOGLE", "GADS", "GOOGLE_ADS");
            case "TIKTOK" -> List.of("TT", "TIKTOK_ADS", "TIKTOK");
            case "FACEBOOK" -> List.of("FB", "META", "META_ADS", "FACEBOOK");
            case "GAM" -> List.of("GOOGLE_GAM", "GAM");
            default -> List.of(canonical);
        };
    }
}
