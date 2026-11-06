package com.affiliate.platform.concurrent;

import com.affiliate.platform.tenant.TenantContext;
import com.affiliate.platform.trace.TraceContext;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 具备多租户 (TenantContext) 与全链路追踪 (TraceContext / SLF4J MDC) 自动透传能力的 Java 21 虚拟线程工具集
 * <p>
 * 解决原生 {@code Thread.ofVirtual()} 与 {@code Executors.newVirtualThreadPerTaskExecutor()}
 * 跨线程执行时丢失 {@link ThreadLocal} 租户上下文与日志 TraceId 的隐患。
 */
public final class ContextAwareVirtualThreads {

    private ContextAwareVirtualThreads() {}

    /**
     * 包装 Runnable，自动捕获父线程的租户上下文、系统执行标志与全链路 TraceId/MDC 并在子线程生命周期内还原与清理
     */
    public static Runnable wrap(Runnable task) {
        if (task == null) {
            return () -> {};
        }
        final String capturedTenant = TenantContext.get();
        final boolean capturedSystemFlag = TenantContext.isSystemExecution();
        final String capturedTrace = TraceContext.get();
        final Map<String, String> capturedMdc = safeCopyMdc();

        return () -> {
            final String prevTenant = TenantContext.get();
            final String prevTrace = TraceContext.get();
            final Map<String, String> prevMdc = safeCopyMdc();
            try {
                if (capturedMdc != null) {
                    safeSetMdc(capturedMdc);
                }
                if (capturedTenant != null) {
                    TenantContext.set(capturedTenant);
                }
                if (capturedTrace != null) {
                    TraceContext.set(capturedTrace);
                }
                if (capturedSystemFlag) {
                    TenantContext.runAsSystem(task);
                } else {
                    task.run();
                }
            } finally {
                if (prevTenant != null) {
                    TenantContext.set(prevTenant);
                } else {
                    TenantContext.clear();
                }
                if (prevTrace != null) {
                    TraceContext.set(prevTrace);
                } else {
                    TraceContext.clear();
                }
                if (prevMdc != null) {
                    safeSetMdc(prevMdc);
                } else {
                    safeClearMdc();
                }
            }
        };
    }

    /**
     * 包装 Callable，自动透传父线程上下文
     */
    public static <V> Callable<V> wrap(Callable<V> task) {
        final String capturedTenant = TenantContext.get();
        final String capturedTrace = TraceContext.get();
        final Map<String, String> capturedMdc = safeCopyMdc();

        return () -> {
            final String prevTenant = TenantContext.get();
            final String prevTrace = TraceContext.get();
            final Map<String, String> prevMdc = safeCopyMdc();
            try {
                if (capturedMdc != null) {
                    safeSetMdc(capturedMdc);
                }
                if (capturedTenant != null) {
                    TenantContext.set(capturedTenant);
                }
                if (capturedTrace != null) {
                    TraceContext.set(capturedTrace);
                }
                return task.call();
            } finally {
                if (prevTenant != null) {
                    TenantContext.set(prevTenant);
                } else {
                    TenantContext.clear();
                }
                if (prevTrace != null) {
                    TraceContext.set(prevTrace);
                } else {
                    TraceContext.clear();
                }
                if (prevMdc != null) {
                    safeSetMdc(prevMdc);
                } else {
                    safeClearMdc();
                }
            }
        };
    }

    /**
     * 装饰任意 Executor，使其提交的所有任务均自动继承调用方线程的租户与追踪上下文
     */
    public static Executor wrapExecutor(Executor delegate) {
        Executor target = delegate != null ? delegate : Executors.newVirtualThreadPerTaskExecutor();
        return command -> target.execute(wrap(command));
    }

    /**
     * 创建自带上下文透传的虚拟线程工厂
     */
    public static ThreadFactory factory(String namePrefix) {
        ThreadFactory base = Thread.ofVirtual().name(namePrefix, 0).factory();
        return r -> base.newThread(wrap(r));
    }

    /**
     * 立即启动一个继承当前租户与追踪上下文的命名虚拟线程
     */
    public static Thread start(String name, Runnable task) {
        return Thread.ofVirtual().name(name).start(wrap(task));
    }

    /**
     * 创建每个任务一个虚拟线程且自动传播上下文的 ExecutorService
     */
    public static ExecutorService newVirtualThreadExecutor(String namePrefix) {
        return Executors.newThreadPerTaskExecutor(factory(namePrefix));
    }

    private static Map<String, String> safeCopyMdc() {
        try {
            return MDC.getCopyOfContextMap();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void safeSetMdc(Map<String, String> mdc) {
        try {
            MDC.setContextMap(mdc);
        } catch (Throwable ignored) {
        }
    }

    private static void safeClearMdc() {
        try {
            MDC.clear();
        } catch (Throwable ignored) {
        }
    }
}
