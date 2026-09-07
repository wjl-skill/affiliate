package com.affiliate.platform.web;

import com.affiliate.platform.service.DomainValidationException;
import com.affiliate.platform.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * 平台全局统一 REST API 异常处理器 (Global API Exception Handler)
 * <p>
 * 统一拦截各类业务与框架异常，转化为规范的 JSON 错误响应体，保障错误格式标准化。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 捕获资源未找到异常 (NotFoundException)，映射为 HTTP 404 NOT_FOUND
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(NotFoundException ex, HttpServletRequest request) {
        return error(404, ex.getMessage(), request);
    }

    /**
     * 捕获入参校验不合法与领域规则冲突异常，映射为 HTTP 400 BAD_REQUEST
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class,
            DomainValidationException.class,
            IllegalStateException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(Exception ex, HttpServletRequest request) {
        return error(400, ex.getMessage(), request);
    }

    /**
     * 捕获安全权限拒绝与签名篡改异常，映射为 HTTP 403 FORBIDDEN
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> forbidden(SecurityException ex, HttpServletRequest request) {
        return error(403, ex.getMessage(), request);
    }

    /**
     * 组装标准错误输出结构体
     *
     * @param status  HTTP 状态码
     * @param message 错误信息描述
     * @param request 当前 HTTP 请求上下文
     * @return 包含时间戳、状态码、错误信息与请求路径的标准错误字典
     */
    private Map<String, Object> error(int status, String message, HttpServletRequest request) {
        return Map.of(
                "timestamp", Instant.now(),
                "status", status,
                "error", message == null ? "Unknown error" : message,
                "path", request.getRequestURI()
        );
    }
}
