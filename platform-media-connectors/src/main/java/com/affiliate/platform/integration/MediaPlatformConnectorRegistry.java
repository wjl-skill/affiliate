package com.affiliate.platform.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 媒体与变现网络连接器策略注册表 (Media Platform Connector Registry)
 * <p>
 * 提供对 Google GAM、TikTok Pangle、Meta Audience Network 及其他媒体变现连接器的归一化注册与动态路由。
 */
@Component
public class MediaPlatformConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(MediaPlatformConnectorRegistry.class);

    private final Map<String, AdPlatformConnector> connectorMap = new ConcurrentHashMap<>();

    @Autowired
    public MediaPlatformConnectorRegistry(List<AdPlatformConnector> connectors) {
        if (connectors != null) {
            for (AdPlatformConnector connector : connectors) {
                registerConnector(connector);
            }
        }

        log.info("[MediaPlatformConnectorRegistry] Initialized with {} media connectors. Supported providers: {}",
                connectors != null ? connectors.size() : 0,
                getSupportedProviders());
    }

    public void registerConnector(AdPlatformConnector connector) {
        if (connector == null || connector.provider() == null) return;
        String canonical = normalizeProvider(connector.provider());
        connectorMap.put(canonical, connector);

        for (String alias : getAliasesFor(canonical)) {
            connectorMap.put(alias, connector);
        }
    }

    public AdPlatformConnector getConnector(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider must not be blank");
        }
        String key = normalizeProvider(provider);
        AdPlatformConnector connector = connectorMap.get(key);
        if (connector == null) {
            throw new IllegalArgumentException("Unsupported media platform connector: " + provider
                    + ". Available: " + getSupportedProviders());
        }
        return connector;
    }

    public boolean hasConnector(String provider) {
        if (provider == null || provider.isBlank()) return false;
        return connectorMap.containsKey(normalizeProvider(provider));
    }

    public Set<String> getSupportedProviders() {
        Set<String> set = new TreeSet<>();
        for (AdPlatformConnector c : connectorMap.values()) {
            set.add(normalizeProvider(c.provider()));
        }
        return Collections.unmodifiableSet(set);
    }

    public static String normalizeProvider(String provider) {
        if (provider == null) return "";
        String clean = provider.trim().toUpperCase().replace("-", "_");
        return switch (clean) {
            case "GAM", "GOOGLE_GAM", "ADMOB", "GOOGLE_AD_MANAGER" -> "GAM";
            case "PANGLE", "TIKTOK_PANGLE", "PANGLE_NETWORK", "TT_PANGLE" -> "PANGLE";
            case "AUDIENCE_NETWORK", "META_FAN", "FB_FAN", "FACEBOOK_AUDIENCE_NETWORK", "FAN", "META_AUDIENCE_NETWORK" -> "AUDIENCE_NETWORK";
            default -> clean;
        };
    }

    private static List<String> getAliasesFor(String canonical) {
        return switch (canonical) {
            case "GAM" -> List.of("GOOGLE_GAM", "ADMOB", "GOOGLE_AD_MANAGER", "GAM");
            case "PANGLE" -> List.of("TIKTOK_PANGLE", "PANGLE_NETWORK", "TT_PANGLE", "PANGLE");
            case "AUDIENCE_NETWORK" -> List.of("META_FAN", "FB_FAN", "FACEBOOK_AUDIENCE_NETWORK", "FAN", "META_AUDIENCE_NETWORK", "AUDIENCE_NETWORK");
            default -> List.of(canonical);
        };
    }
}
