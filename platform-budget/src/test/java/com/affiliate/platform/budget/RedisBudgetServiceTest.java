package com.affiliate.platform.budget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisBudgetServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RedisBudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new RedisBudgetService(redisTemplate);
    }

    @Test
    void testSetBudget() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        budgetService.setBudget("tenant_1", "camp_1", new BigDecimal("100.50"));

        verify(valueOperations).set(
                eq("{budget:tenant_1:camp_1}:daily"),
                eq("100500000"),
                eq(Duration.ofDays(2))
        );
    }

    @Test
    void testReserveSuccess() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(90000000L); // 剩余金额

        BudgetService.Reservation reservation = budgetService.reserve(
                "tenant_1", "camp_1", "user_1", new BigDecimal("10.00")
        );

        assertNotNull(reservation);
        assertEquals("tenant_1", reservation.tenantId());
        assertEquals("camp_1", reservation.campaignId());
        assertEquals(new BigDecimal("10.00"), reservation.amount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                keysCaptor.capture(),
                eq("10000000"), // 10 USD in micros
                eq(reservation.id()),
                anyString(), // expireEpoch
                eq("3600")   // safety ttl
        );

        List<String> keys = keysCaptor.getValue();
        assertEquals(3, keys.size());
        assertEquals("{budget:tenant_1:camp_1}:daily", keys.get(0));
        assertEquals("{budget:tenant_1:camp_1}:res:" + reservation.id(), keys.get(1));
        assertEquals("{budget:tenant_1:camp_1}:active_res", keys.get(2));
    }

    @Test
    void testReserveExhaustedThrowsException() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(-2L); // 余额不足

        assertThrows(IllegalStateException.class, () ->
                budgetService.reserve("tenant_1", "camp_1", "user_1", new BigDecimal("1000.00"))
        );
    }

    @Test
    void testConfirmExecutesLuaWithActiveSet() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(1L);

        BudgetService.Reservation res = new BudgetService.Reservation(
                "res_123", "tenant_1", "camp_1", "user_1", new BigDecimal("5.00")
        );

        budgetService.confirm(res);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                keysCaptor.capture(),
                eq("res_123")
        );

        List<String> keys = keysCaptor.getValue();
        assertEquals("{budget:tenant_1:camp_1}:res:res_123", keys.get(0));
        assertEquals("{budget:tenant_1:camp_1}:active_res", keys.get(1));
        assertEquals("{budget:tenant_1:camp_1}:confirmed:res_123", keys.get(2));
    }

    @Test
    void testReleaseExecutesLuaWithReplenish() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(1L);

        BudgetService.Reservation res = new BudgetService.Reservation(
                "res_456", "tenant_1", "camp_1", "user_1", new BigDecimal("5.00")
        );

        budgetService.release(res);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                keysCaptor.capture(),
                eq("res_456")
        );

        List<String> keys = keysCaptor.getValue();
        assertEquals("{budget:tenant_1:camp_1}:daily", keys.get(0));
        assertEquals("{budget:tenant_1:camp_1}:res:res_456", keys.get(1));
        assertEquals("{budget:tenant_1:camp_1}:active_res", keys.get(2));
        assertEquals("{budget:tenant_1:camp_1}:released:res_456", keys.get(3));
    }

    @Test
    void testSweepExpiredReservations() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(eq("{budget:tenant_1:camp_1}:active_res"), eq(0.0), anyDouble()))
                .thenReturn(Set.of("expired_res_1", "expired_res_2"));

        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(1L);

        int sweepCount = budgetService.sweepExpiredReservations("tenant_1", "camp_1");

        assertEquals(2, sweepCount, "应当成功回收 2 笔超期预占");
        verify(redisTemplate, times(2)).execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        );
    }
}
