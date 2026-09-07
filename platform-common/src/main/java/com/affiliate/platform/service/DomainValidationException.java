package com.affiliate.platform.service;

/**
 * 领域实体模型校验异常 (Domain Validation Exception)
 * <p>
 * 当业务实体在紧凑构造器或服务层校验不通过（如尺寸非正、底价为负、URL 为空白）时抛出该异常，
 * 由顶层统一异常处理器转换为 400 Bad Request 响应给调用方。
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }

    /**
     * 指定字段与失败原因构造校验异常
     *
     * @param field  校验失败的属性字段名称（例如 "width", "floorPrice"）
     * @param reason 具体的业务原因说明（例如 "must be positive"）
     */
    public DomainValidationException(String field, String reason) {
        super("Validation failed for field [" + field + "]: " + reason);
    }
}
