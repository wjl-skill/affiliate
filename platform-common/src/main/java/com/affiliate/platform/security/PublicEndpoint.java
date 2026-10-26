package com.affiliate.platform.security;

import java.lang.annotation.*;

/**
 * 显式标识该 REST 端点为对外公开数据面或免鉴权接口 (Public Endpoint Annotation)
 * <p>
 * 遵循阶段 0 与阶段 1 安全底线规范：
 * 任何未标注 @PreAuthorize 的 REST Handler 必须显式声明 @PublicEndpoint，
 * 严禁任何业务接口在无权限校验或无显式公开声明的状态下裸奔上线。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {

    /**
     * 免鉴权公开原因说明 (如: "Public click tracking redirect", "OpenRTB data-plane")
     */
    String reason() default "Public data-plane or unauthenticated entry point";
}
