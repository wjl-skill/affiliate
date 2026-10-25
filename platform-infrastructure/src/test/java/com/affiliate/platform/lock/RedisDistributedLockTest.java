package com.affiliate.platform.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式锁单元测试 (Redis Distributed Lock Unit Tests)
 */
class RedisDistributedLockTest {

    @Test
    @DisplayName("1. 验证单机内存降级模式下分布式锁的获取、互斥与安全释放")
    void testMemoryFallbackAcquisitionAndRelease() {
        RedisDistributedLock lockService = new RedisDistributedLock(null);
        String lockKey = "lock:test:rule_001";

        // 第一次成功获取锁
        Optional<DistributedLock.LockHandle> lock1 = lockService.tryAcquire(lockKey, Duration.ofSeconds(10));
        assertTrue(lock1.isPresent(), "应当成功获取锁句柄");
        assertTrue(lock1.get().isHeld());

        // 第二次在锁未释放时应当被互斥拦截
        Optional<DistributedLock.LockHandle> lock2 = lockService.tryAcquire(lockKey, Duration.ofSeconds(10));
        assertFalse(lock2.isPresent(), "已存在的活跃锁应当互斥拦截二次获取");

        // 释放第一次获取的锁
        lock1.get().close();
        assertFalse(lock1.get().isHeld());

        // 再次获取应当成功
        Optional<DistributedLock.LockHandle> lock3 = lockService.tryAcquire(lockKey, Duration.ofSeconds(10));
        assertTrue(lock3.isPresent(), "锁释放后应能重新获取");
        lock3.get().close();
    }

    @Test
    @DisplayName("2. 验证租期过期后自动失效机制")
    void testLockLeaseTimeout() throws InterruptedException {
        RedisDistributedLock lockService = new RedisDistributedLock(null);
        String lockKey = "lock:test:expire_001";

        // 设置极短租期 50ms
        Optional<DistributedLock.LockHandle> lock1 = lockService.tryAcquire(lockKey, Duration.ofMillis(50));
        assertTrue(lock1.isPresent());

        // 等待 70ms 超过租期
        Thread.sleep(70);

        // 应当能够自动获取过期锁
        Optional<DistributedLock.LockHandle> lock2 = lockService.tryAcquire(lockKey, Duration.ofSeconds(5));
        assertTrue(lock2.isPresent(), "超过租期后锁应自动失效并允许重新竞争获取");
        lock2.get().close();
    }
}
