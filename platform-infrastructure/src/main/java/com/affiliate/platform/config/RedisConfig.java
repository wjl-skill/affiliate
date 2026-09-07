package com.affiliate.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 广告联盟分布式 Redis 高可用连接与模板配置 (Redis Distributed Infrastructure Configuration)
 * <p>
 * 提供生产级 Redis 装配支撑：
 * 1. StringRedisTemplate：用于微秒级高并发原子计数、预算预占 Lua 与频控滑动窗口 ZSET；
 * 2. RedisTemplate&lt;String, Object&gt;：统一采用 JSON 序列化，杜绝 Java 原生序列化二进制乱码；
 * 3. 启动诊断探针：在容器就绪 (ApplicationReadyEvent) 时输出连接元数据并执行快速 PING 探针检测。
 */
@Configuration
@ConditionalOnProperty(name = "app.infrastructure.redis-enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    private final RedisProperties redisProperties;

    public RedisConfig(@Autowired(required = false) RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    /**
     * 针对通用业务对象的 Redis 操作模板，使用 String 作为 Key，JSON 作为 Value
     *
     * @param connectionFactory Redis 连接工厂
     * @return 序列化定制的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // 统一 Key 序列化规则
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 统一 Value 序列化规则（JSON 格式便于跨语言调试与运维查看）
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 字符串高频操作专用模板 (StringRedisTemplate)
     *
     * @param connectionFactory Redis 连接工厂
     * @return StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 应用程序就绪事件探活自检，输出详细连接元数据及健康状态
     *
     * @param event 容器就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkRedisHealth(ApplicationReadyEvent event) {
        try {
            RedisConnectionFactory factory = event.getApplicationContext().getBean(RedisConnectionFactory.class);
            String host = (redisProperties != null && redisProperties.getHost() != null) ? redisProperties.getHost() : "127.0.0.1";
            int port = (redisProperties != null && redisProperties.getPort() != 0) ? redisProperties.getPort() : 6379;
            int database = (redisProperties != null) ? redisProperties.getDatabase() : 0;
            boolean hasPassword = (redisProperties != null && redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank());

            try (RedisConnection connection = factory.getConnection()) {
                String ping = connection.ping();
                log.info(">>> [RedisConfig] Successfully connected to Redis at {}:{} (DB: {}, Auth: {}) - PING: {}",
                        host, port, database, hasPassword ? "Configured" : "None", ping);
            } catch (Exception e) {
                log.warn(">>> [RedisConfig] Failed to ping Redis at {}:{}. Error: {}. System will gracefully fallback to L1 Guava cache if available.",
                        host, port, e.getMessage());
            }
        } catch (Exception e) {
            log.warn(">>> [RedisConfig] Redis connection factory bean not found or failed to initialize: {}", e.getMessage());
        }
    }
}
