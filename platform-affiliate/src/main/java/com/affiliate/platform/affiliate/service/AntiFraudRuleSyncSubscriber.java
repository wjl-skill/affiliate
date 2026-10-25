package com.affiliate.platform.affiliate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 4D 反欺诈规则变更 Redis Pub/Sub 热同步监听器 (Anti-Fraud Rule Sync Redis Subscriber)
 * <p>
 * 订阅频道 `channel:antifraud:rule_sync`，在分布式多节点集群中实现毫秒级风控规则热生效。
 */
@Component
@ConditionalOnProperty(name = "app.infrastructure.redis-enabled", havingValue = "true")
public class AntiFraudRuleSyncSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AntiFraudRuleSyncSubscriber.class);
    public static final String CHANNEL_NAME = "channel:antifraud:rule_sync";

    private final AffiliateAntiFraudEngine antiFraudEngine;

    @Autowired
    public AntiFraudRuleSyncSubscriber(AffiliateAntiFraudEngine antiFraudEngine) {
        this.antiFraudEngine = antiFraudEngine;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) return;
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("[AntiFraudRuleSyncSubscriber] Received remote rule sync event: {}", payload);
            antiFraudEngine.applyRemoteRuleSync(payload);
        } catch (Exception ex) {
            log.error("[AntiFraudRuleSyncSubscriber] Error applying remote rule sync message: {}", ex.getMessage(), ex);
        }
    }
}
