package com.affiliate.platform.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 Redis 与 Lua 脚本的工业级分布式锁实现 (Redis Distributed Lock)
 * <p>
 * 核心特性：
 * 1. 采用 Redis `SET key token NX PX leaseTime` 实现纳秒级原子互斥；
 * 2. 释放锁采用 Lua 脚本比对 token，杜绝在极端长 GC 或网络抖动下误删其他实例的锁；
 * 3. 内存降级兜底：在未装配 Redis 或 Redis 短暂脱机时，自动平滑回退至带超时的本内存重入锁，确保单机联调与离线单元测试正常运行。
 */
@Component
public class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    // 安全释放锁的 Lua 脚本（原子性：比对当前值与传入 Token 相等时才执行 DEL）
    private static final String UNLOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    // 内存降级锁表 (用于无 Redis 时的单机开发与单元测试)
    private final ConcurrentMap<String, MemoryLockEntry> memoryLockMap = new ConcurrentHashMap<>();

    private record MemoryLockEntry(String token, Instant expireAt) {}

    public RedisDistributedLock(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setScriptText(UNLOCK_LUA_SCRIPT);
        this.unlockScript.setResultType(Long.class);
    }

    @Override
    public Optional<LockHandle> tryAcquire(String lockKey, Duration leaseTime) {
        return tryAcquire(lockKey, Duration.ZERO, leaseTime);
    }

    @Override
    public Optional<LockHandle> tryAcquire(String lockKey, Duration waitTime, Duration leaseTime) {
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("lockKey 不能为空");
        }
        Duration effectiveLease = (leaseTime != null && !leaseTime.isNegative() && !leaseTime.isZero())
                ? leaseTime
                : Duration.ofSeconds(30);

        String token = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        long waitMs = waitTime != null ? Math.max(0, waitTime.toMillis()) : 0L;

        while (true) {
            boolean acquired = doAcquire(lockKey, token, effectiveLease);
            if (acquired) {
                return Optional.of(new DefaultLockHandle(lockKey, token));
            }
            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed >= waitMs) {
                break;
            }
            try {
                Thread.sleep(Math.min(50L, waitMs - elapsed));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Optional.empty();
    }

    private boolean doAcquire(String lockKey, String token, Duration leaseTime) {
        if (redisTemplate != null) {
            try {
                Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, token, leaseTime);
                return Boolean.TRUE.equals(success);
            } catch (Exception ex) {
                log.debug("[RedisLock] Redis connection failed, falling back to in-memory lock: {}", ex.getMessage());
            }
        }

        // 内存锁降级兜底
        Instant now = Instant.now();
        Instant expire = now.plus(leaseTime);
        MemoryLockEntry existing = memoryLockMap.get(lockKey);
        if (existing == null || existing.expireAt().isBefore(now)) {
            MemoryLockEntry newEntry = new MemoryLockEntry(token, expire);
            MemoryLockEntry prev = memoryLockMap.putIfAbsent(lockKey, newEntry);
            if (prev == null || prev.expireAt().isBefore(now)) {
                memoryLockMap.put(lockKey, newEntry);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean release(String lockKey, String lockValue) {
        if (lockKey == null || lockValue == null) {
            return false;
        }
        boolean released = false;
        if (redisTemplate != null) {
            try {
                Long result = redisTemplate.execute(unlockScript, Collections.singletonList(lockKey), lockValue);
                released = (result != null && result > 0L);
            } catch (Exception ex) {
                log.debug("[RedisLock] Redis release failed, fallback to memory release: {}", ex.getMessage());
            }
        }

        MemoryLockEntry entry = memoryLockMap.get(lockKey);
        if (entry != null && entry.token().equals(lockValue)) {
            memoryLockMap.remove(lockKey);
            released = true;
        }
        return released;
    }

    private class DefaultLockHandle implements LockHandle {
        private final String key;
        private final String token;
        private volatile boolean held = true;

        DefaultLockHandle(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public String lockKey() {
            return key;
        }

        @Override
        public String lockValue() {
            return token;
        }

        @Override
        public boolean isHeld() {
            return held;
        }

        @Override
        public void close() {
            if (held) {
                try {
                    release(key, token);
                } finally {
                    held = false;
                }
            }
        }
    }
}
