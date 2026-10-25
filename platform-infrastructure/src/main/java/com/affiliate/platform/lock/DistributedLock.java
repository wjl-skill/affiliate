package com.affiliate.platform.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * 分布式锁统一抽象接口 (Distributed Lock Specification)
 * <p>
 * 支持基于 Redis / DB 的分布式高可用并发控制，提供带租赁超时（Lease Time）的安全防死锁锁句柄。
 */
public interface DistributedLock {

    /**
     * 自动释放资源的分布式锁句柄
     */
    interface LockHandle extends AutoCloseable {
        String lockKey();
        String lockValue();
        boolean isHeld();

        @Override
        void close();
    }

    /**
     * 非阻塞尝试获取锁
     *
     * @param lockKey   锁唯一键
     * @param leaseTime 自动过期租期
     * @return 成功返回 LockHandle，失败返回 Optional.empty()
     */
    Optional<LockHandle> tryAcquire(String lockKey, Duration leaseTime);

    /**
     * 带超时等待尝试获取锁
     *
     * @param lockKey   锁唯一键
     * @param waitTime  最大等待耗时
     * @param leaseTime 自动过期租期
     * @return 成功返回 LockHandle，失败返回 Optional.empty()
     */
    Optional<LockHandle> tryAcquire(String lockKey, Duration waitTime, Duration leaseTime);

    /**
     * 显式安全释放锁（校验 Token 归属，杜绝误释放其他节点的锁）
     */
    boolean release(String lockKey, String lockValue);
}
