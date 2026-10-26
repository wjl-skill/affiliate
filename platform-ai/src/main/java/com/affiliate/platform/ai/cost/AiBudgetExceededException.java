package com.affiliate.platform.ai.cost;

/**
 * 租户 AI 预算超限异常 (AI Daily Budget Exceeded Exception)
 */
public class AiBudgetExceededException extends RuntimeException {

    private final String tenantId;

    public AiBudgetExceededException(String tenantId, String message) {
        super(message);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
