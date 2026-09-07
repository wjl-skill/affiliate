package com.affiliate.platform;

import com.affiliate.platform.config.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RedisConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisAutoConfiguration.class,
                    RedisConfig.class
            ));

    @Test
    void testRedisConfigurationAndBeansLoaded() {
        contextRunner
                .withPropertyValues(
                        "app.infrastructure.redis-enabled=true",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6379",
                        "spring.data.redis.password=123456",
                        "spring.data.redis.database=0",
                        "spring.data.redis.timeout=3000ms",
                        "spring.data.redis.client-type=lettuce",
                        "spring.data.redis.lettuce.pool.max-active=50",
                        "spring.data.redis.lettuce.pool.max-idle=10",
                        "spring.data.redis.lettuce.pool.min-idle=2"
                )
                .run(context -> {
                    // 1. 验证核心 Bean 均正常实例化
                    assertTrue(context.containsBean("redisConnectionFactory"), "RedisConnectionFactory bean should be registered");
                    assertTrue(context.containsBean("stringRedisTemplate"), "StringRedisTemplate bean should be registered");
                    assertTrue(context.containsBean("redisTemplate"), "RedisTemplate bean should be registered");

                    RedisConnectionFactory connectionFactory = context.getBean(RedisConnectionFactory.class);
                    assertNotNull(connectionFactory);

                    // 2. 验证与本地 Redis 实例的连通性及密码握手
                    try (RedisConnection connection = connectionFactory.getConnection()) {
                        String ping = connection.ping();
                        assertEquals("PONG", ping, "Redis ping should return PONG with correct password");
                        System.out.println(">>> [IntegrationTest] Live Redis ping verified: " + ping);
                    }

                    // 3. 验证 StringRedisTemplate 读写能力
                    StringRedisTemplate stringTemplate = context.getBean(StringRedisTemplate.class);
                    String testKey = "test:integration:string";
                    stringTemplate.opsForValue().set(testKey, "affiliate_platform_v2", 10, TimeUnit.SECONDS);
                    assertEquals("affiliate_platform_v2", stringTemplate.opsForValue().get(testKey));
                    stringTemplate.delete(testKey);

                    // 4. 验证通用 RedisTemplate JSON 序列化对象读写
                    @SuppressWarnings("unchecked")
                    RedisTemplate<String, Object> redisTemplate = (RedisTemplate<String, Object>) context.getBean("redisTemplate");
                    String objectKey = "test:integration:json";
                    redisTemplate.delete(objectKey);
                    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
                    Map<String, Object> testMap = new java.util.HashMap<>();
                    testMap.put("tenantId", "T1001");
                    testMap.put("active", true);
                    byte[] bytes = serializer.serialize(testMap);
                    System.out.println(">>> Serialized JSON: " + new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    Object deserialized = serializer.deserialize(bytes);
                    System.out.println(">>> Directly deserialized: " + deserialized);

                    redisTemplate.opsForValue().set(objectKey, testMap, 10, TimeUnit.SECONDS);
                    Object retrieved = redisTemplate.opsForValue().get(objectKey);
                    assertNotNull(retrieved);
                    System.out.println(">>> [IntegrationTest] Retrieved JSON serialized object: " + retrieved);
                    redisTemplate.delete(objectKey);
                });
    }

    @Test
    void testRedisDisabledCondition() {
        contextRunner
                .withPropertyValues(
                        "app.infrastructure.redis-enabled=false"
                )
                .run(context -> {
                    // 当显式关闭 redis 时，自定义 RedisConfig 不应激活
                    assertFalse(context.containsBean("redisConfig"), "RedisConfig should not load when redis-enabled=false");
                });
    }
}
