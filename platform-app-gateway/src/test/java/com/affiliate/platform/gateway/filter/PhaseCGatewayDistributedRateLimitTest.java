package com.affiliate.platform.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("Phase C 验证：Gateway 分布式 Redis 集群限流与故障本地降级测试")
class PhaseCGatewayDistributedRateLimitTest {

    @Test
    @DisplayName("1. 验证分布式 Redis 计数限流触发 429 与剩余配额响应头")
    @SuppressWarnings("unchecked")
    void testDistributedRedisRateLimitEnforcement() {
        ReactiveStringRedisTemplate mockRedis = Mockito.mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> mockOps = Mockito.mock(ReactiveValueOperations.class);
        AtomicLong counter = new AtomicLong(0);

        when(mockRedis.opsForValue()).thenReturn(mockOps);
        when(mockOps.increment(anyString())).thenAnswer(inv -> Mono.just(counter.incrementAndGet()));
        when(mockRedis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        GatewayRateLimitGlobalFilter filter = new GatewayRateLimitGlobalFilter();
        filter.setControlplaneQps(2);
        filter.setRedisTemplate(mockRedis);

        GatewayFilterChain passChain = exchange -> Mono.empty();

        for (int i = 1; i <= 2; i++) {
            MockServerWebExchange ex = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/campaigns").header("X-Forwarded-For", "198.51.100.5").build()
            );
            filter.filter(ex, passChain).block();
            assertNull(ex.getResponse().getStatusCode());
            assertEquals("2", ex.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
            assertEquals(String.valueOf(2 - i), ex.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        }

        MockServerWebExchange blockedEx = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/campaigns").header("X-Forwarded-For", "198.51.100.5").build()
        );
        filter.filter(blockedEx, passChain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blockedEx.getResponse().getStatusCode());
        assertEquals(1L, filter.getTotalRejected());
    }

    @Test
    @DisplayName("2. 验证 Redis 异常时自动优雅降级为本地滑动窗口限流")
    @SuppressWarnings("unchecked")
    void testFallbackToLocalCounterOnRedisError() {
        ReactiveStringRedisTemplate mockRedis = Mockito.mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> mockOps = Mockito.mock(ReactiveValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(mockOps);
        when(mockOps.increment(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        GatewayRateLimitGlobalFilter filter = new GatewayRateLimitGlobalFilter();
        filter.setDataplaneQps(1);
        filter.setRedisTemplate(mockRedis);

        GatewayFilterChain passChain = exchange -> Mono.empty();

        MockServerWebExchange ex1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/rtb/openrtb/2.5/bid").header("X-Forwarded-For", "203.0.113.10").build()
        );
        filter.filter(ex1, passChain).block();
        assertNull(ex1.getResponse().getStatusCode());

        MockServerWebExchange ex2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/rtb/openrtb/2.5/bid").header("X-Forwarded-For", "203.0.113.10").build()
        );
        filter.filter(ex2, passChain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex2.getResponse().getStatusCode());
    }
}
