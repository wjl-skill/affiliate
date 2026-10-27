package com.affiliate.platform.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 媒体与变现网络连接器注册表单元测试
 */
class MediaPlatformConnectorRegistryTest {

    private MediaPlatformConnectorRegistry registry;
    private GoogleGamConnector gamConnector;
    private TikTokPangleConnector pangleConnector;
    private MetaAudienceNetworkConnector fanConnector;

    @BeforeEach
    void setUp() {
        gamConnector = new GoogleGamConnector();
        pangleConnector = new TikTokPangleConnector();
        fanConnector = new MetaAudienceNetworkConnector();
        registry = new MediaPlatformConnectorRegistry(List.of(gamConnector, pangleConnector, fanConnector));
    }

    @Test
    @DisplayName("测试支持的媒体网络列表包含 GAM, PANGLE, AUDIENCE_NETWORK")
    void testSupportedProviders() {
        Set<String> providers = registry.getSupportedProviders();
        assertTrue(providers.contains("GAM"));
        assertTrue(providers.contains("PANGLE"));
        assertTrue(providers.contains("AUDIENCE_NETWORK"));
    }

    @Test
    @DisplayName("测试别名归一化与连接器路由解析")
    void testConnectorRoutingAndAliases() {
        // GAM 别名测试
        assertSame(gamConnector, registry.getConnector("GAM"));
        assertSame(gamConnector, registry.getConnector("google_gam"));
        assertSame(gamConnector, registry.getConnector("ADMOB"));
        assertSame(gamConnector, registry.getConnector("google-ad-manager"));

        // Pangle 别名测试
        assertSame(pangleConnector, registry.getConnector("PANGLE"));
        assertSame(pangleConnector, registry.getConnector("tiktok_pangle"));
        assertSame(pangleConnector, registry.getConnector("tt_pangle"));
        assertSame(pangleConnector, registry.getConnector("pangle-network"));

        // Meta FAN 别名测试
        assertSame(fanConnector, registry.getConnector("AUDIENCE_NETWORK"));
        assertSame(fanConnector, registry.getConnector("meta_fan"));
        assertSame(fanConnector, registry.getConnector("fb_fan"));
        assertSame(fanConnector, registry.getConnector("facebook_audience_network"));
        assertSame(fanConnector, registry.getConnector("fan"));
    }

    @Test
    @DisplayName("测试未注册的未知媒体渠道抛出异常")
    void testUnknownProviderThrowsException() {
        assertFalse(registry.hasConnector("UNKNOWN_SSP"));
        assertThrows(IllegalArgumentException.class, () -> registry.getConnector("UNKNOWN_SSP"));
    }
}
