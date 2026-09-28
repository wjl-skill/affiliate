package com.affiliate.platform.tenant;

/**
 * 基于线程局部变量 (ThreadLocal) 的多租户上下文传递器 (Multi-Tenant Context Holder)
 * <p>
 * 在当前执行线程生命周期内绑定并维护租户标识 tenantId，
 * 确保各层业务服务、数据仓储与消息事件在无显式传参的情况下实现租户数据安全隔离。
 */
public final class TenantContext {

    // 线程局部存储：维护当前请求线程绑定的租户标识字符串
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    /**
     * 绑定租户标识到当前线程
     *
     * @param tenantId 租户唯一标识符
     */
    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    /**
     * 获取当前线程绑定的租户标识
     *
     * @return 租户标识；未绑定时返回 null
     */
    public static String get() {
        return CURRENT.get();
    }

    /**
     * 严格获取当前线程租户标识；若未绑定则直接阻断并抛出异常
     *
     * @return 确定的租户标识
     * @throws IllegalStateException 当前请求上下文缺失租户绑定时抛出
     */
    public static String required() {
        String value = get();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("tenant context is required");
        }
        return value;
    }

    /**
     * 清理当前线程绑定的租户上下文，防止线程池复用导致的上下文泄漏污染
     */
    public static void clear() {
        CURRENT.remove();
    }
}
