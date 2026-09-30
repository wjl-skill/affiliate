package com.affiliate.platform.trace;

import org.slf4j.MDC;

/**
 * 分布式全链路追踪上下文 (Distributed Trace Context)
 * <p>
 * 串联 HTTP 请求、点击路由、S2S 回传、对账与结算链路，并自动同步维护 SLF4J MDC 上下文。
 */
public final class TraceContext {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String REQUEST_HEADER = "X-Request-Id";
    public static final String MDC_TRACE_KEY = "traceId";

    private static final ThreadLocal<String> CURRENT_TRACE = new ThreadLocal<>();

    private TraceContext() {}

    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            CURRENT_TRACE.set(traceId);
            try {
                MDC.put(MDC_TRACE_KEY, traceId);
            } catch (Throwable ignored) {
            }
        } else {
            clear();
        }
    }

    public static String get() {
        return CURRENT_TRACE.get();
    }

    public static String getOrCreate() {
        String current = CURRENT_TRACE.get();
        if (current == null || current.isBlank()) {
            current = java.util.UUID.randomUUID().toString().replace("-", "");
            set(current);
        }
        return current;
    }

    public static void clear() {
        CURRENT_TRACE.remove();
        try {
            MDC.remove(MDC_TRACE_KEY);
        } catch (Throwable ignored) {
        }
    }
}
