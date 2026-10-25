package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.web.AntiFraudConfigController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AntiFraudRuleSyncTest {

    private StringRedisTemplate redisTemplate;
    private AffiliateAntiFraudEngine engine;
    private AntiFraudRuleSyncSubscriber subscriber;
    private AntiFraudConfigController controller;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        engine = new AffiliateAntiFraudEngine(null, null, redisTemplate);
        subscriber = new AntiFraudRuleSyncSubscriber(engine);
        controller = new AntiFraudConfigController(engine);
    }

    @Test
    void testGetDefaultRules() {
        ResponseEntity<Map<String, Object>> response = controller.getRules();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(3L, response.getBody().get("minCtitSeconds"));
        assertEquals(2592000L, response.getBody().get("maxCtitSeconds"));
    }

    @Test
    void testUpdateRulesAndRedisPublish() {
        var request = new AntiFraudConfigController.RuleUpdateRequest(5L, 3600L, false, true);
        ResponseEntity<?> resp = controller.updateRules(request);
        assertEquals(200, resp.getStatusCode().value());

        var cfg = engine.getRuleConfig();
        assertEquals(5L, cfg.minCtitSeconds());
        assertEquals(3600L, cfg.maxCtitSeconds());
        assertFalse(cfg.enableDatacenterCheck());
        assertTrue(cfg.enableBotUaCheck());

        // 验证向 Redis 发布了变更事件
        verify(redisTemplate, times(1)).convertAndSend(
                eq("channel:antifraud:rule_sync"),
                eq("5:3600:false:true")
        );
    }

    @Test
    void testSubscriberReceivesRemoteSync() {
        String payload = "10:7200:true:false";
        DefaultMessage msg = new DefaultMessage(
                "channel:antifraud:rule_sync".getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(msg, null);

        var cfg = engine.getRuleConfig();
        assertEquals(10L, cfg.minCtitSeconds());
        assertEquals(7200L, cfg.maxCtitSeconds());
        assertTrue(cfg.enableDatacenterCheck());
        assertFalse(cfg.enableBotUaCheck());
    }

    @Test
    void testBlacklistAndMetrics() {
        controller.addIpBlacklist("192.168.1.100");
        controller.addSubIdBlacklist("bad_traffic_sub");

        assertEquals(1, engine.getIpBlacklistSize());
        assertEquals(1, engine.getSubIdBlacklistSize());

        ResponseEntity<Map<String, Object>> metrics = controller.getMetrics();
        assertEquals(200, metrics.getStatusCode().value());
        assertEquals(1, metrics.getBody().get("ipBlacklistSize"));
        assertEquals(1, metrics.getBody().get("subIdBlacklistSize"));
    }
}
