package com.affiliate.platform.security;

import com.affiliate.platform.tenant.TenantContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * JWT 令牌多租户与权限转换器 (Tenant JWT Authentication Converter)
 * <p>
 * 从解码后的 JWT Claim 中解析租户声明（tenant_id 或 tenant），
 * 自动绑定至底层 TenantContext，并将角色权限映射为 Spring Security Authorities。
 */
public final class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    // Spring 标准 JWT 权限转换工具
    private final JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();

    /**
     * 将 JWT 解密令牌转换为框架可识别的身份认证凭证
     *
     * @param jwt 解密通过的 JWT 实例
     * @return 包含权限与租户绑定的身份凭证对象
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 1. 优先提取 "tenant_id" 声明
        String tenant = jwt.getClaimAsString("tenant_id");
        if (tenant == null || tenant.isBlank()) {
            // 2. 兼容提取 "tenant" 简写声明
            tenant = jwt.getClaimAsString("tenant");
        }

        // 3. 提取成功则将租户直接注入当前执行线程上下文
        if (tenant != null && !tenant.isBlank()) {
            TenantContext.set(tenant);
        }

        // 4. 返回标准 JWT 认证令牌
        return new JwtAuthenticationToken(jwt, authorities.convert(jwt), jwt.getSubject());
    }
}
