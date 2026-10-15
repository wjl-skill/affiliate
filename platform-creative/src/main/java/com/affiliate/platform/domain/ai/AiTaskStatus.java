package com.affiliate.platform.domain.ai;

/**
 * AI 异步任务生命周期状态 (AI Task Lifecycle Status)
 */
public enum AiTaskStatus {

    /**
     * 已接收：客户端已提交请求，尚未派发给上游供应商
     */
    SUBMITTED("SUBMITTED", "已提交"),

    /**
     * 排队中：已创建本地任务单，完成参数校验与算力额度预占
     */
    PENDING("PENDING", "排队中"),

    /**
     * 生成处理中：上游模型厂商已受理，正在推理渲染
     */
    PROCESSING("PROCESSING", "生成中"),

    /**
     * 已拉取临时结果：已从厂商轮询到临时下载链接，等待转存私有 OSS
     */
    SUCCESS_FETCHED("SUCCESS_FETCHED", "已拉取临时结果"),

    /**
     * 生成成功终态：已完成私有 S3 OSS 转存，入库素材物料，实扣算力
     */
    SUCCESS("SUCCESS", "生成成功"),

    /**
     * 生成失败终态：上游超时、敏感词拦截、参数不合法等
     */
    FAILED("FAILED", "生成失败"),

    /**
     * 已取消终态：用户主动取消任务，释放预占算力
     */
    CANCELLED("CANCELLED", "已取消"),

    /**
     * 已废弃终态：临时拉取结果过期或系统自动清理
     */
    DISCARDED("DISCARDED", "已废弃");

    private final String code;
    private final String description;

    AiTaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == DISCARDED;
    }
}
